import { defineUserConfig, defaultTheme } from 'vuepress';

export default defineUserConfig({
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
              text: '第一章',
              link: '/stage1/',
              collapsible: true,
              children: [
                {
                    text: '小节 1',
                    link: '/stage1/phase1/',
                    children: [],
                },
                {
                    text: '小节 2',
                    link: '/stage1/phase2/',
                    children: [],
                },
                {
                    text: '小节 3',
                    link: '/stage1/phase3/',
                    children: [],
                },
                {
                    text: '小节 4',
                    link: '/stage1/phase4/',
                    children: [],
                },
                {
                    text: '小节 5',
                    link: '/stage1/phase5/',
                    children: [],
                },
                {
                    text: '小节 6',
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
                        text: '小节 1',
                        link: '/stage2/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 2',
                        link: '/stage2/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 3',
                        link: '/stage2/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 4',
                        link: '/stage2/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5',
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
                        text: '小节 1',
                        link: '/stage3/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 2',
                        link: '/stage3/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 3',
                        link: '/stage3/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 4',
                        link: '/stage3/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5',
                        link: '/stage3/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 6',
                        link: '/stage3/phase6/',
                        children: [],
                    },
                ],
            },
            {
                text: '第四章',
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
                text: '第五章',
                link: '/stage5/',
                collapsible: true,
                children: [
                    {
                        text: '小节 1',
                        link: '/stage5/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 2',
                        link: '/stage5/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 3',
                        link: '/stage5/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 4',
                        link: '/stage5/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5',
                        link: '/stage5/phase5/',
                        children: [],
                    },
                    {
                        text: '小节 6',
                        link: '/stage5/phase6/',
                        children: [],
                    },
                    {
                        text: '小节 7',
                        link: '/stage5/phase7/',
                        children: [],
                    },
                    {
                        text: '小节 8',
                        link: '/stage5/phase8/',
                        children: [],
                    },
                ],
            },
            {
                text: '第六章',
                link: '/stage6/',
                collapsible: true,
                children: [
                    {
                        text: '小节 1',
                        link: '/stage6/phase1/',
                        children: [],
                    },
                    {
                        text: '小节 2',
                        link: '/stage6/phase2/',
                        children: [],
                    },
                    {
                        text: '小节 3',
                        link: '/stage6/phase3/',
                        children: [],
                    },
                    {
                        text: '小节 4',
                        link: '/stage6/phase4/',
                        children: [],
                    },
                    {
                        text: '小节 5',
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
                text: '第七章',
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
                text: '第八章',
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
                text: '第九章',
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