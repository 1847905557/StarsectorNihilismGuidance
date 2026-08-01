import { viteBundler } from '@vuepress/bundler-vite'
import { defaultTheme } from '@vuepress/theme-default'
import { defineUserConfig } from 'vuepress'

import { createSidebar, sidebarDiscoveryPlugin } from './sidebar.js'

const REPO_URL =
  'https://github.com/1847905557/StarsectorNihilismGuidance'
const HOSTNAME = 'https://1847905557.github.io'
const BASE = '/StarsectorNihilismGuidance/'
const SITE_URL = `${HOSTNAME}${BASE}`

export default defineUserConfig({
  bundler: viteBundler(),
  plugins: [sidebarDiscoveryPlugin()],

  lang: 'zh-CN',
  title: 'Starsector Nihilism Guidance',
  description: '虚无主义教程 - Starsector Nihilism Guidance',
  base: BASE,
  theme: defaultTheme({
    hostname: HOSTNAME,
    themePlugins: {
      seo: {
        canonical: ({ path }) => new URL(path.slice(1), SITE_URL).href,
      },
    },
    repo: REPO_URL,
    repoLabel: 'GitHub 项目',
    docsDir: 'docs',
    navbarLabel: '主导航',
    pageNavbarLabel: '页面导航',
    navbar: [{ text: '首页', link: '/' }],
    sidebar: createSidebar(),
    selectLanguageText: '选择语言',
    selectLanguageAriaLabel: '选择语言',
    editLinkText: '在 GitHub 上编辑此页',
    lastUpdatedText: '上次更新',
    contributorsText: '贡献者',
    tip: '提示',
    warning: '注意',
    danger: '警告',
    important: '重要',
    note: '注释',
    notFound: ['页面不存在'],
    backToHome: '返回首页',
    openInNewWindow: '在新窗口中打开',
    toggleColorMode: '切换颜色模式',
    toggleSidebar: '切换侧边栏',
    prev: '上一页',
    next: '下一页',
  }),
})
