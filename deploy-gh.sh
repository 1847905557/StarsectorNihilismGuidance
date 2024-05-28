#!/usr/bin/env sh

# Exception catch
set -e

# Install vuepress
pnpm add -D vuepress@next vue
pnpm add -D @vuepress/bundler-vite@next @vuepress/theme-default@next

# Build file
pnpm run docs:build

# Into dir and deploy
cd docs/.vuepress/dist

git init
git add -A
git commit -m 'deploy'

git push -f git@github.com:1847905557/StarsectorNihilismGuidance.git master:gh-pages

cd -