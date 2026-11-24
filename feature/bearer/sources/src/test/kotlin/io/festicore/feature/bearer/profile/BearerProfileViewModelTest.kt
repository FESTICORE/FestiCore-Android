package io.festicore.feature.bearer.profile

import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagPhoto
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerInvite
import io.festicore.domain.bearer.entity.BearerPhotoUploadEvent
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.feature.bearer.claim.FakeBearerUseCase
import io.festicore.feature.bearer.claim.SampleProfile
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `BearerProfileFeatureTests` 13건(함수 이름을 iOS와 같게 둔다) + Android 추가 `photoLoadFailed` · `sampleSize`. */
class BearerProfileViewModelTest {
    private fun newState(): BearerProfileState =
        BearerProfileState.initial(festivalId = "fst_1", flagId = "flg_1", profile = SampleProfile, isNewBearer = true)

    private fun viewModel(
        useCase: FakeBearerUseCase = FakeBearerUseCase(),
        profile: BearerFlagProfile? = SampleProfile,
        isNewBearer: Boolean = true,
    ) = BearerProfileViewModel(
        festivalId = "fst_1",
        flagId = "flg_1",
        profile = profile,
        isNewBearer = isNewBearer,
        bearerUseCase = useCase,
    )

    // 코드 등록 직후에는 서버 임시 이름과 기본 태그를 비워 둔다
    @Test
    fun placeholderCleared() {
        val state = newState()
        assertTrue(state.name.isEmpty())
        assertTrue(state.tags.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.canSave)
    }

    // 오늘 계획은 프로필의 고른 공연을 들고 누르면 부모에게 내 일정을 요청한다
    @Test
    fun planOpensMySchedule() = runTest {
        val profile = BearerFlagProfile(id = "flg_1", name = "호락호락", plan = listOf("set_1", "set_2"))
        viewModel(profile = profile, isNewBearer = false).test(this) {
            assertEquals(listOf("set_1", "set_2"), containerHost.container.stateFlow.value.plan)
            containerHost.onPlanTapped()
            expectSideEffect(BearerProfileSideEffect.PlanRequested(festivalId = "fst_1"))
        }
    }

    // 공동 기수는 나를 빼고 세어 3명까지 보인다
    @Test
    fun coBearerText() {
        val state = newState()
        assertEquals("0 / 3명", state.coBearerText)
        assertEquals("3 / 3명", state.copy(bearerCount = 4).coBearerText)
    }

    // 팩의 존·축제 이름과 기수 수를 채운다
    @Test
    fun loads() = runTest {
        val festival = BearerFestival(id = "fst_1", name = "원더리벳 2026", zones = listOf(BearerZone(id = "zone_a", name = "리벳 스테이지 앞 왼쪽")))
        val board =
            BearerBoard(
                profile = SampleProfile,
                status =
                    BearerFlagStatus(
                        flagId = "flg_1",
                        lifecycle = BearerFlagLifecycle.Scheduled,
                        zoneId = null,
                        activity = null,
                        updatedAt = Instant.EPOCH,
                    ),
                followerCount = 0,
                coBearers = listOf(BearerCoBearer(userId = "usr_1", nickname = "로이"), BearerCoBearer(userId = "usr_2", nickname = "민지")),
            )
        val useCase = FakeBearerUseCase(onFetchFestival = { festival }, onFetchBoard = { board })
        viewModel(useCase).test(this) {
            containerHost.onAppear()
            expectState { copy(festivalName = "원더리벳 2026", zones = festival.zones, bearerCount = 2) }
        }
    }

    // 소개는 서버처럼 코드포인트 60개까지 받는다
    @Test
    fun bioCountsScalars() = runTest {
        val flag = "🏳️‍🌈"
        val text = flag.repeat(20)
        viewModel().test(this) {
            containerHost.onBioChanged(text)
            expectState { copy(bio = BearerProfileViewModel.prefix(text, 60)) }
            val bio = containerHost.container.stateFlow.value.bio
            assertEquals(60, bio.codePointCount(0, bio.length))
            assertTrue(bio.length < text.length)
            assertEquals(15, bio.split(flag).size - 1)
        }
    }

    // 태그는 3개까지 고르고 다시 누르면 뺀다
    @Test
    fun tags() = runTest {
        viewModel().test(this) {
            containerHost.onTagTapped(BearerFlagTag.Slam)
            expectState { copy(tags = listOf(BearerFlagTag.Slam)) }
            containerHost.onTagTapped(BearerFlagTag.Sing)
            expectState { copy(tags = listOf(BearerFlagTag.Slam, BearerFlagTag.Sing)) }
            containerHost.onTagTapped(BearerFlagTag.Newbie)
            expectState { copy(tags = listOf(BearerFlagTag.Slam, BearerFlagTag.Sing, BearerFlagTag.Newbie)) }
            containerHost.onTagTapped(BearerFlagTag.Calm)
            containerHost.onTagTapped(BearerFlagTag.Sing)
            expectState { copy(tags = listOf(BearerFlagTag.Slam, BearerFlagTag.Newbie)) }
        }
    }

    // 저장하면 완료 화면으로 바뀌고 이름 중복이면 이름 칸에 오류를 보인다
    @Test
    fun save() = runTest {
        val saved = BearerFlagProfile(id = "flg_1", name = "호락호락", tags = listOf(BearerFlagTag.Slam))
        val state = newState().copy(name = " 호락호락 ", tags = listOf(BearerFlagTag.Slam), homeZoneId = "zone_a")
        val useCase =
            FakeBearerUseCase(onUpdateProfile = { flagId, change ->
                assertEquals("flg_1", flagId)
                assertEquals(
                    BearerProfileChange(
                        name = "호락호락",
                        bio = "",
                        tags = listOf(BearerFlagTag.Slam),
                        planVisible = true,
                        homeZoneId = "zone_a",
                    ),
                    change,
                )
                saved
            })
        viewModel(useCase).test(this, initialState = state) {
            containerHost.onSaveTapped()
            expectState { copy(isSaving = true) }
            expectState { copy(isSaving = false, savedProfile = saved) }
            containerHost.onDoneTapped()
            expectSideEffect(BearerProfileSideEffect.Finished(flagId = "flg_1"))
        }
    }

    // 오늘 계획을 다시 고르면 저장할 때 plan을 함께 보내고, 고르지 않았으면 보내지 않는다
    @Test
    fun savesEditedPlan() = runTest {
        val saved = BearerFlagProfile(id = "flg_1", name = "호락호락", tags = listOf(BearerFlagTag.Slam))
        val state = newState().copy(name = "호락호락", tags = listOf(BearerFlagTag.Slam))
        assertNull(state.change.plan)
        val useCase =
            FakeBearerUseCase(onUpdateProfile = { _, change ->
                assertEquals(listOf("set_2", "set_1"), change.plan)
                saved
            })
        viewModel(useCase).test(this, initialState = state) {
            containerHost.onPlanChanged(listOf("set_2", "set_1"))
            expectState { copy(plan = listOf("set_2", "set_1"), isPlanEdited = true) }
            containerHost.onSaveTapped()
            expectState { copy(isSaving = true) }
            expectState { copy(isSaving = false, isPlanEdited = false, savedProfile = saved) }
        }
    }

    // 이름이 겹치면 이름 칸에 오류를 보이고 고치면 지운다
    @Test
    fun nameTaken() = runTest {
        val state = newState().copy(name = "호락호락", tags = listOf(BearerFlagTag.Slam))
        viewModel().test(this, initialState = state) {
            containerHost.onSaveFailed(BearerError.NameTaken())
            expectState { copy(nameError = "이미 있는 깃발 이름이에요. 다른 이름을 지어 주세요.") }
            assertFalse(containerHost.container.stateFlow.value.canSave)
            containerHost.onNameChanged("호락호락2")
            expectState { copy(name = "호락호락2", nameError = null) }
        }
    }

    // 사진은 고르는 즉시 올리며 진행률을 보이고 끝나면 채운다
    @Test
    fun uploadsPhoto() = runTest {
        val data = byteArrayOf(1, 2, 3)
        val photo = BearerFlagPhoto(photoUrl = "https://festicore.test/p", thumbnailUrl = "https://festicore.test/t")
        val useCase =
            FakeBearerUseCase(onUploadPhoto = { _, imageData ->
                assertTrue(imageData.contentEquals(data))
                flowOf(BearerPhotoUploadEvent.Progress(0.64), BearerPhotoUploadEvent.Completed(photo))
            })
        viewModel(useCase).test(this) {
            containerHost.onPhotoPicked(data)
            expectState { copy(photoUpload = BearerPhotoUpload.Uploading(data = data, progress = 0.0)) }
            assertFalse(containerHost.container.stateFlow.value.canSave)
            expectState { copy(photoUpload = BearerPhotoUpload.Uploading(data = data, progress = 0.64)) }
            expectState { copy(photoUpload = BearerPhotoUpload.Idle, uploadedPhotoData = data, photoUrl = photo.thumbnailUrl) }
        }
    }

    // 업로드를 취소하면 전송을 멈추고 이전 사진을 둔다
    @Test
    fun cancelsUpload() = runTest {
        val data = byteArrayOf(1)
        var cancelled = false
        val useCase =
            FakeBearerUseCase(onUploadPhoto = { _, _ ->
                flow {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                }
            })
        viewModel(useCase).test(this) {
            containerHost.onPhotoPicked(data)
            expectState { copy(photoUpload = BearerPhotoUpload.Uploading(data = data, progress = 0.0)) }
            containerHost.onPhotoCancelTapped()
            expectState { copy(photoUpload = BearerPhotoUpload.Idle) }
        }
        assertTrue(cancelled)
    }

    // 작은 사진은 이유를 보인다
    @Test
    fun photoTooSmall() = runTest {
        val state = newState().copy(photoUpload = BearerPhotoUpload.Uploading(data = byteArrayOf(1), progress = 0.0))
        viewModel().test(this, initialState = state) {
            containerHost.onPhotoFailed(BearerError.PhotoTooSmall())
            expectState { copy(photoUpload = BearerPhotoUpload.Failed("256×256보다 큰 사진을 골라 주세요")) }
        }
    }

    // 공동 기수 초대 코드를 팝업으로 보인다
    @Test
    fun invites() = runTest {
        val invite = BearerInvite(code = "ABC234", expiresAt = Instant.EPOCH)
        viewModel(FakeBearerUseCase(onInviteCoBearer = { invite })).test(this) {
            containerHost.onInviteTapped()
            expectState { copy(isInviting = true) }
            expectState { copy(isInviting = false, alert = BearerProfileAlert.Invite(code = "ABC234")) }
            containerHost.onAlertDismissed()
            expectState { copy(alert = null) }
        }
    }

    // Android 추가: 고른 사진을 기기에서 읽지 못하면 다시 고르라는 문구를 보이고, 저장 중이면 무시한다
    @Test
    fun photoLoadFailed() = runTest {
        viewModel().test(this) {
            containerHost.onPhotoLoadFailed()
            expectState { copy(photoUpload = BearerPhotoUpload.Failed("사진을 올리지 못했어요. 다시 골라 주세요")) }
        }
        viewModel().test(this, initialState = newState().copy(isSaving = true)) {
            containerHost.onPhotoLoadFailed()
        }
    }

    // Android 추가: 미리보기는 짧은 변이 표시 크기 아래로 내려가지 않게 2의 거듭제곱으로 줄여 디코딩한다
    @Test
    fun sampleSize() {
        assertEquals(1, sampleSize(width = 200, height = 300, sidePx = 264))
        assertEquals(8, sampleSize(width = 4032, height = 3024, sidePx = 264))
        assertEquals(2, sampleSize(width = 600, height = 8000, sidePx = 264))
    }
}
