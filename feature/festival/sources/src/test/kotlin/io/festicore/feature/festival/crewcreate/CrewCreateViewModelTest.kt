package io.festicore.feature.festival.crewcreate

import io.festicore.domain.festival.entity.FestivalBoardSelection
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.crew.FakeCrewUseCase
import io.festicore.feature.festival.liveboard.FakeBoardSelectionUseCase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `FestivalCrewCreateFeatureTests`. */
class CrewCreateViewModelTest {
    private val initial = CrewCreateState(festivalId = "festival-1")

    private fun viewModel(
        crewUseCase: FakeCrewUseCase = FakeCrewUseCase(),
        selection: FakeBoardSelectionUseCase = FakeBoardSelectionUseCase(),
    ) = CrewCreateViewModel(festivalId = "festival-1", crewUseCase = crewUseCase, boardSelectionUseCase = selection)

    /** 만들면 초대 단계로 바뀌고 크루장 초대 링크를 만든 뒤 크루 화면으로 보낸다. */
    @Test
    fun createsCrewThenInvitesAndOpensCrew() = runTest {
        val calls = mutableListOf<Pair<String, String>>()
        val useCase =
            FakeCrewUseCase(create = { festivalId, name ->
                calls += festivalId to name
                invitedCrew
            })
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onNameChanged("  우리 크루  ")
            expectState { copy(name = "  우리 크루  ") }
            containerHost.onSubmitTapped()
            expectState { copy(isSubmitting = true) }
            val created = awaitState()
            assertEquals(initial.copy(name = "  우리 크루  ", step = CrewCreateStep.Created(invitedCrew)), created)
            assertEquals("festicore://crew/K7P2QX", created.inviteUrl)
            containerHost.onOpenCrewTapped()
            expectSideEffect(CrewCreateSideEffect.OpenCrewRequested("crew-1"))
        }
        assertEquals(listOf("festival-1" to "우리 크루"), calls)
    }

    /** 이미 크루가 있거나 끝난 페스티벌이면 입력란 아래에 알리고 이름을 고치면 지운다. */
    @Test
    fun showsPermanentFailureInline() = runTest {
        viewModel(FakeCrewUseCase(create = { _, _ -> throw FestivalCrewError.AlreadyInCrew() })).test(this, initialState = initial) {
            containerHost.onNameChanged("우리 크루")
            expectState { copy(name = "우리 크루") }
            containerHost.onSubmitTapped()
            expectState { copy(isSubmitting = true) }
            expectState { copy(isSubmitting = false) }
            expectState { copy(errorMessage = "이 페스티벌에서 이미 크루에 있어요. 크루는 페스티벌마다 하나만 만들거나 참여할 수 있어요.") }
            containerHost.onNameChanged("새 크루")
            expectState { copy(name = "새 크루", errorMessage = null) }
        }
    }

    /** 로그인이 풀렸으면 부모에게 로그인을 요청한다. */
    @Test
    fun requestsLoginWhenUnauthenticated() = runTest {
        viewModel(FakeCrewUseCase(create = { _, _ -> throw FestivalCrewError.Unauthenticated() })).test(this, initialState = initial) {
            containerHost.onNameChanged("우리 크루")
            expectState { copy(name = "우리 크루") }
            containerHost.onSubmitTapped()
            expectState { copy(isSubmitting = true) }
            expectState { copy(isSubmitting = false) }
            expectSideEffect(CrewCreateSideEffect.LoginRequested)
        }
    }

    /** 빈 이름과 공백만 있는 이름은 제출되지 않는다. 이름은 12자에서 자른다. */
    @Test
    fun rejectsBlankName() = runTest {
        viewModel().test(this, initialState = initial) {
            containerHost.onSubmitTapped()
            containerHost.onNameChanged("   ")
            expectState { copy(name = "   ") }
            containerHost.onSubmitTapped()
            containerHost.onNameChanged("열세글자가넘는아주긴크루이름")
            expectState { copy(name = "열세글자가넘는아주긴크루") }
        }
    }

    /** 생성 실패는 다시 시도할 수 있는 공용 다이얼로그를 보여 준다. */
    @Test
    fun showsRetryAlert() = runTest {
        var attempts = 0
        val useCase =
            FakeCrewUseCase(create = { _, _ ->
                attempts += 1
                throw FestivalCrewError.Unavailable()
            })
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onNameChanged("우리 크루")
            expectState { copy(name = "우리 크루") }
            containerHost.onSubmitTapped()
            expectState { copy(isSubmitting = true) }
            expectState { copy(isSubmitting = false) }
            expectState { copy(isRetryDialogPresented = true) }
            containerHost.onRetryConfirmed()
            expectState { copy(isRetryDialogPresented = false) }
            expectState { copy(isSubmitting = true) }
            expectState { copy(isSubmitting = false) }
            expectState { copy(isRetryDialogPresented = true) }
        }
        assertEquals(2, attempts)
    }

    /** 생성 실패 다이얼로그를 닫으면 이름을 유지하고 재요청하지 않는다. */
    @Test
    fun dismissesRetryAlert() = runTest {
        val presented = initial.copy(name = "우리 크루", isRetryDialogPresented = true)
        viewModel().test(this, initialState = presented) {
            containerHost.onRetryDismissed()
            val dismissed = awaitState()
            assertEquals(presented.copy(isRetryDialogPresented = false), dismissed)
            assertTrue(dismissed.canSubmit)
        }
    }

    /** 제출 중에는 닫지 않고, 아니면 부모에게 닫기를 요청한다. */
    @Test
    fun closeIsBlockedWhileSubmitting() = runTest {
        viewModel().test(this, initialState = initial.copy(name = "우리 크루", isSubmitting = true)) {
            containerHost.onCloseTapped()
        }
        viewModel().test(this, initialState = initial) {
            containerHost.onCloseTapped()
            expectSideEffect(CrewCreateSideEffect.CloseRequested)
        }
    }

    /** 안내 마지막 줄에 보드가 고른 같은 축제 이름만 넣는다(iOS `rulesText`). */
    @Test
    fun rulesUseSelectedFestivalName() {
        val selection = FakeBoardSelectionUseCase()
        selection.update(
            FestivalBoardSelection(
                festivalId = "festival-1",
                document = FestivalPackDocument(id = "festival-1", name = "원더리벳 2026"),
                isOnlineDetailLoading = false,
                isOnlineDetailFailed = false,
                isPackWatchFailed = false,
                connection = FestivalBoardSelection.Connection.Live,
                packCheckedAt = null,
            ),
        )
        val state = viewModel(selection = selection).container.stateFlow.value
        assertEquals("· 원더리벳 2026이 끝나면 기록만 남아요", state.rules.last())
        assertEquals("· 페스티벌이 끝나면 기록만 남아요", initial.rules.last())
        assertNull(initial.inviteUrl)
    }

    private val invitedCrew =
        FestivalCrew(
            id = "crew-1",
            festivalId = "festival-1",
            name = "우리 크루",
            ownerId = "user-1",
            members = emptyList(),
            inviteCode = "K7P2QX",
            inviteExpiresAt = Instant.ofEpochSecond(1_800_172_800),
        )
}
