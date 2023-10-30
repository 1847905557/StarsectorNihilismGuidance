#!/usr/bin/env sh

# Exception catch
set -e

# Install vuepress
yarn add -D vuepress@next

# Build file
npm run docs:build

# Into dir and deploy
cd docs/.vuepress/dist

git init
git add -A
git commit -m 'deploy'

git push -f git@github.com:1847905557/StarsectorNihilismGuidance.git master:gh-pages

cd -