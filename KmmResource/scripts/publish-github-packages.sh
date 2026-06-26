#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

GITHUB_PACKAGES_USERNAME="${GITHUB_PACKAGES_USERNAME:-${GITHUB_ACTOR:-}}"
GITHUB_PACKAGES_TOKEN="${GITHUB_PACKAGES_TOKEN:-${GITHUB_TOKEN:-${GH_TOKEN:-}}}"

if [[ -z "$GITHUB_PACKAGES_USERNAME" ]]; then
  echo "GITHUB_PACKAGES_USERNAME or GITHUB_ACTOR is required." >&2
  exit 1
fi

if [[ -z "$GITHUB_PACKAGES_TOKEN" ]]; then
  echo "GITHUB_PACKAGES_TOKEN, GITHUB_TOKEN, or GH_TOKEN is required." >&2
  exit 1
fi

default_publish_tasks=(
  ":resource-generator:publishAllPublicationsToGithubPackagesRepository"
  ":resource-core:publishAndroidPublicationToGithubPackagesRepository"
  ":resource-core:publishIosX64PublicationToGithubPackagesRepository"
  ":resource-core:publishIosArm64PublicationToGithubPackagesRepository"
  ":resource-core:publishIosSimulatorArm64PublicationToGithubPackagesRepository"
  ":resource-core:publishOhosArm64PublicationToGithubPackagesRepository"
  ":resource-core:publishKotlinMultiplatformPublicationToGithubPackagesRepository"
)
DEFAULT_KMMRESOURCE_PUBLISH_TASKS="${default_publish_tasks[*]}"
KMMRESOURCE_PUBLISH_TASKS="${KMMRESOURCE_PUBLISH_TASKS:-$DEFAULT_KMMRESOURCE_PUBLISH_TASKS}"
IFS=' ' read -r -a publish_tasks <<< "$KMMRESOURCE_PUBLISH_TASKS"
KMMRESOURCE_REQUIRE_TASKS="${KMMRESOURCE_REQUIRE_TASKS:-false}"
KMMRESOURCE_DRY_RUN="${KMMRESOURCE_DRY_RUN:-false}"

gradle_args=(
  "--no-daemon"
  "--console=plain"
  "-PkmmResourcePublishOnly=true"
  "-PgithubPackagesUsername=$GITHUB_PACKAGES_USERNAME"
  "-PgithubPackagesToken=$GITHUB_PACKAGES_TOKEN"
)

if [[ -n "${MAVEN_VERSION:-}" ]]; then
  gradle_args+=("-Pversion=$MAVEN_VERSION" "-PmavenVersion=$MAVEN_VERSION")
fi

if [[ -n "${GITHUB_PACKAGES_OWNER:-}" ]]; then
  gradle_args+=("-PgithubPackagesOwner=$GITHUB_PACKAGES_OWNER")
fi

if [[ -n "${GITHUB_PACKAGES_REPOSITORY:-}" ]]; then
  gradle_args+=("-PgithubPackagesRepository=$GITHUB_PACKAGES_REPOSITORY")
fi

task_cache_dir="$(mktemp -d)"
trap 'rm -rf "$task_cache_dir"' EXIT

task_exists() {
  local task_path="$1"
  local project_path="${task_path%:*}"
  local task_name="${task_path##*:}"
  local cache_name="${project_path//:/_}"
  local task_file="$task_cache_dir/${cache_name:-root}.tasks"

  if [[ ! -f "$task_file" ]]; then
    if ! ./gradlew --no-daemon --console=plain -PkmmResourcePublishOnly=true "$project_path:tasks" --all > "$task_file" 2>&1; then
      cat "$task_file" >&2
      return 2
    fi
  fi

  grep -Eq "^[[:space:]]*$task_name([[:space:]]|$|-)" "$task_file"
}

available_publish_tasks=()
missing_publish_tasks=()
for publish_task in "${publish_tasks[@]}"; do
  if task_exists "$publish_task"; then
    available_publish_tasks+=("$publish_task")
  else
    task_status=$?
    if [[ "$task_status" -eq 2 ]]; then
      echo "Unable to discover Gradle publish tasks." >&2
      exit 1
    fi
    missing_publish_tasks+=("$publish_task")
  fi
done

if (( ${#missing_publish_tasks[@]} > 0 )); then
  if [[ "$KMMRESOURCE_REQUIRE_TASKS" == "true" ]]; then
    echo "Missing required publish tasks on this host:" >&2
    printf '  %s\n' "${missing_publish_tasks[@]}" >&2
    exit 1
  fi

  echo "Skipping publish tasks unavailable on this host:"
  printf '  %s\n' "${missing_publish_tasks[@]}"
fi

if (( ${#available_publish_tasks[@]} == 0 )); then
  echo "No publish tasks are available on this host." >&2
  exit 1
fi

echo "Publishing KmmResource publications to GitHub Packages:"
printf '  %s\n' "${available_publish_tasks[@]}"

if [[ "$KMMRESOURCE_DRY_RUN" == "true" ]]; then
  ./gradlew "${gradle_args[@]}" --dry-run "${available_publish_tasks[@]}"
else
  ./gradlew "${gradle_args[@]}" "${available_publish_tasks[@]}"
fi
