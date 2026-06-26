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
  ":network:publishAndroidPublicationToGithubPackagesRepository"
  ":network:publishIosX64PublicationToGithubPackagesRepository"
  ":network:publishIosArm64PublicationToGithubPackagesRepository"
  ":network:publishIosSimulatorArm64PublicationToGithubPackagesRepository"
  ":network:publishOhosArm64PublicationToGithubPackagesRepository"
  ":network-ohos-runtime:publishAllPublicationsToGithubPackagesRepository"
  ":network-ohos-runtime-gradle-plugin:publishAllPublicationsToGithubPackagesRepository"
  ":network:publishKotlinMultiplatformPublicationToGithubPackagesRepository"
)
DEFAULT_NETWORK_PUBLISH_TASKS="${default_publish_tasks[*]}"
NETWORK_PUBLISH_TASKS="${NETWORK_PUBLISH_TASKS:-$DEFAULT_NETWORK_PUBLISH_TASKS}"
IFS=' ' read -r -a publish_tasks <<< "$NETWORK_PUBLISH_TASKS"

gradle_args=(
  "--no-daemon"
  "--console=plain"
  "-PgithubPackagesUsername=$GITHUB_PACKAGES_USERNAME"
  "-PgithubPackagesToken=$GITHUB_PACKAGES_TOKEN"
)

if [[ -n "${MAVEN_VERSION:-}" ]]; then
  gradle_args+=("-PmavenVersion=$MAVEN_VERSION")
fi

if [[ -n "${GITHUB_PACKAGES_OWNER:-}" ]]; then
  gradle_args+=("-PgithubPackagesOwner=$GITHUB_PACKAGES_OWNER")
fi

if [[ -n "${GITHUB_PACKAGES_REPOSITORY:-}" ]]; then
  gradle_args+=("-PgithubPackagesRepository=$GITHUB_PACKAGES_REPOSITORY")
fi

echo "Publishing NetworkKMM publications to GitHub Packages:"
printf '  %s\n' "${publish_tasks[@]}"

./gradlew "${gradle_args[@]}" "${publish_tasks[@]}"
