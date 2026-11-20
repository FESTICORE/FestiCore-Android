# Git과 PR 작업

## 작업 시작

- 모든 작업은 GitHub 이슈에서 시작한다.
- 기존 파일과 모듈 의존성을 먼저 확인한 뒤 수정한다. 다른 작업자의 미커밋 변경은 보존한다.
- 변경 범위를 좁게 유지하고 기능별 커밋으로 분리한다.
- 10분 동안 의미 있는 편집이 없으면 현재 진행 상황과 다음 접근을 사용자에게 알린다.

## 브랜치와 커밋

- `feature/<작업명>`(영문 소문자 kebab-case) 브랜치에서 작업하며 PR 대상은 `develop`이다. `develop` · `main`에는 직접 커밋 · 푸시하지 않는다.
- 브랜치 이름에 이슈 번호를 넣지 않는다. 각 커밋 제목 끝에는 해당 작업 이슈 번호를 `#27`처럼 붙인다.
- 커밋 제목과 본문을 한국어로 작성한다. 타입 접두사(`feat:`)는 쓰지 않는다. Gradle 설정, core, ui, 문서 등 독립적인 변경은 나누어 커밋한다.
- 커밋 · PR 본문에 `Co-Authored-By` 트레일러(작성자 무관)와 세션 링크 같은 AI 표시를 넣지 않는다. 훅과 CI(`pr-rules.yml`)가 막는다.
- 다른 작업자의 수정이 섞여 있을 수 있으므로 `git status`와 `git diff`를 확인하고 소유 파일만 stage한다.
- PR 줄 수 제한은 두지 않는다. 함께 검증할 큰 묶음은 2,000줄을 넘어도 한 PR로 올리고 억지로 쪼개지 않는다. 검증이 끝난 PR은 squash merge한다.

## 스쿼시 머지

- 머지는 **squash merge만** 쓴다. 저장소에서 merge commit · rebase merge는 꺼 두었다.
- 스쿼시 커밋은 **제목 한 줄만** 남긴다. 제목은 PR 제목에 GitHub가 ` (#PR번호)`를 붙인 것이다(예: `하네스 규칙과 훅을 추가한다 #1 (#2)`). 본문은 비운다. 작업 내용 · 검증은 PR 본문에 남긴다.
- 저장소 설정으로 강제한다: squash 제목 `PR_TITLE`, 본문 `BLANK`, 머지 시 원격 브랜치 삭제. 확인:
  `gh api repos/FESTICORE/FestiCore-Android --jq '.squash_merge_commit_title, .squash_merge_commit_message'` → `PR_TITLE` · `BLANK`

```bash
gh pr checks {PR번호} --watch                   # CI가 모두 통과할 때까지 기다린다
gh pr merge {PR번호} --squash --delete-branch --body ""
git switch develop && git pull --ff-only
git fetch --prune
test "$(gh pr view {PR번호} --json state -q .state)" = MERGED && git branch -D feature/{작업명}
```

- 스쿼시 머지는 기록이 달라 `git branch -d`가 거부하므로, PR이 `MERGED`인지 확인하고 `-D`로 지운다.

## PR 검증

PR을 생성할 때 `gh pr create --assignee Roy-wonji`로 작성자를 담당자로 지정한다. 다른 방법으로 PR을 만들었다면 `gh pr edit <PR 번호> --add-assignee Roy-wonji`를 실행한다. 생성 직후 `gh pr view <PR 번호> --json assignees`로 할당 여부를 확인한다. `.github/workflows/assign-pr-owner.yml`도 생성·재개·담당자 해제·새 커밋 시 누락된 할당을 복구한다.

PR 본문에는 변경 범위, 테스트 명령과 결과, 실행하지 못한 검증을 구분해 쓴다. PR 라벨은 저장소에 있는 작업 유형 라벨을 사용한다. CI 성공과 로컬 테스트, 에뮬레이터 · 실기기 검증을 같은 결과로 표현하지 않는다. 이슈는 작업 PR이 합쳐지고 요구 사항을 확인한 뒤 닫는다.

AI 리뷰가 요청되면 결과가 도착했는지 확인한다. 리뷰 요청만으로 승인이나 완료를 추정하지 않는다.
외부 리뷰 API의 일일 할당량 초과로 작업이 경고와 함께 끝나면 해당 HEAD의 리뷰는 미완료로 기록한다. CI 작업 성공 표시만으로 AI 리뷰 완료를 주장하지 않는다.
