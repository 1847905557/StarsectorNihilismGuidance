import { viteBundler } from '@vuepress/bundler-vite'
import { defaultTheme } from '@vuepress/theme-default'
import { defineUserConfig } from 'vuepress'

export default defineUserConfig({
    bundler: viteBundler(),

    lang: 'zh-CN',
    title: 'Starsector Nihilism Guidance',
    description: '虚无主义教程 - Starsector Nihilism Guidance',
    base: '/StarsectorNihilismGuidance/',
    theme: defaultTheme({
        docsRepo: 'https://github.com/1847905557/StarsectorNihilismGuidance',
        docsBranch: 'main',
        docsDir: 'docs',
        editLinkPattern: ':repo/edit/:branch/:path',
        navbar: [
            {
                text: '首页',
                link: '/'
            },
            {
                text: 'Github 项目',
                link: 'https://github.com/1847905557/StarsectorNihilismGuidance'
            },
        ],
        sidebar: [
            {
                text: '首页',
                link: '/',
            },
            {
              text: '第一章 基础及舰船相关',
              link: '/stage1/',
              collapsible: true,
              children: [
                {
                    text: '小节 MOD结构',
                    link: '/stage1/phase1/',
                    children: [],
                },
                {
                    text: '小节 基本舰船',
                    link: '/stage1/phase2/',
                    children: [],
                },
                {
                    text: '小节 航母、战机与相位舰',
                    link: '/stage1/phase3/',
                    children: [],
                },
                {
                    text: '小节 武器',
                    link: '/stage1/phase4/',
                    children: [],
                },
                {
                    text: '小节 战术系统',
                    link: '/stage1/phase5/',
                    children: [],
                },
                {
                    text: '小节 舰船插件',
                    link: '/stage1/phase6/',
                    children: [],
                },
              ],
            },
            {
                text: '第二章',
                link: '/stage2/',
                collapsible: true,
                children: [
                    {
                        text: '小节 战役',
                        link: '/stage2/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 势力',
                        link: '/stage2/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 星系',
                        link: '/stage2/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 描述文本',
                        link: '/stage2/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 内置装配',
                        link: '/stage2/phase5/',
                        children: [],
                    },
                ],
            },
            {
                text: '第三章',
                link: '/stage3/',
                collapsible: true,
                children: [
                    {
                        text: '小节 模拟战',
                        link: '/stage3/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 舰船风格',
                        link: '/stage3/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 模块船',
                        link: '/stage3/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 势力争霸',
                        link: '/stage3/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 弹体尾迹',
                        link: '/stage3/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 音效相关',
                        link: '/stage3/phase6/',
                        children: [],
                    },
                ],
            },
            {
                text: '第四章 等待设计',
                link: '/stage4/',
                collapsible: true,
                children: [
                    {
                        text: '小节 1',
                        link: '/stage4/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 2',
                        link: '/stage4/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 3',
                        link: '/stage4/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 4',
                        link: '/stage4/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5',
                        link: '/stage4/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 6',
                        link: '/stage4/phase6/',
                        children: [],
                    },
                ],
            },
            {
                text: '第五章 核心代码',
                link: '/stage5/',
                collapsible: true,
                children: [
                    {
                        text: '小节 ModPlugin',
                        link: '/stage5/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 Global',
                        link: '/stage5/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 CombatEngine 等待设计',
                        link: '/stage5/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 CombatEntity 等待设计',
                        link: '/stage5/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5 等待设计',
                        link: '/stage5/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 船插 等待设计',
                        link: '/stage5/phase6/',
                        children: [],
                    },
                    {
                        text: '小节 战术系统AI 等待设计',
                        link: '/stage5/phase7/',
                        children: [],
                    },
                    {
                        text: '小节 开火/命中 等待设计',
                        link: '/stage5/phase8/',
                        children: [],
                    },
                ],
            },
            {
                text: '第六章 进阶 等待设计',
                link: '/stage6/',
                collapsible: true,
                children: [
                    {
                        text: '小节 BaseEveryFrameCombatPlugin',
                        link: '/stage6/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 BaseCombatLayeredRenderingPlugin',
                        link: '/stage6/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 EveryFrameWeaponEffectPlugin',
                        link: '/stage6/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 AI',
                        link: '/stage6/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 动画设计',
                        link: '/stage6/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 6',
                        link: '/stage6/phase6/',
                        children: [],
                    },
                ],
            },
            {
                text: '第七章 等待设计',
                link: '/stage7/',
                collapsible: true,
                children: [
                    {
                        text: '小节 1',
                        link: '/stage7/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 2',
                        link: '/stage7/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 3',
                        link: '/stage7/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 4',
                        link: '/stage7/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5',
                        link: '/stage7/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 6',
                        link: '/stage7/phase6/',
                        children: [],
                    },
                    {
                        text: '小节 7',
                        link: '/stage7/phase7/',
                        children: [],
                    },
                ],
            },
            {
                text: '第八章 等待设计',
                link: '/stage8/',
                collapsible: true,
                children: [
                    {
                        text: '小节 1',
                        link: '/stage8/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 2',
                        link: '/stage8/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 3',
                        link: '/stage8/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 4',
                        link: '/stage8/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5',
                        link: '/stage8/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 6',
                        link: '/stage8/phase6/',
                        children: [],
                    },
                ],
            },
            {
                text: '第九章 等待设计',
                link: '/stage9/',
                collapsible: true,
                children: [
                    {
                        text: '小节 1',
                        link: '/stage9/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 2',
                        link: '/stage9/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 3',
                        link: '/stage9/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 4',
                        link: '/stage9/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5',
                        link: '/stage9/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 6',
                        link: '/stage9/phase6/',
                        children: [],
                    },
                ],
            },
        ],
    })
});