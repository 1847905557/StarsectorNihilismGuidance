module.exports = {
    plugins: [
        ['vuepress-plugin-side-anchor']
    ],

    themeConfig: {
        sidebarDepth: 2,

        sidebar: {
            
            '/': [

                '',  //该目录下的README.md文件

                
                {
                    title: '第一天',
                    children: [
                        'day1/stage1/',
                        'day1/stage2/',
                        'day1/stage3/',
                        'day1/stage4/',
                        'day1/stage5/',
                        'day1/stage6/',
                    ],
                },

                {
                    title: '第二天',
                    children: [
                        'day2/stage1/',
                        'day2/stage2/',
                        'day2/stage3/',
                        'day2/stage4/',
                        'day2/stage5/',
                    ],
                },

                {
                    title: '第八天',
                    children: [
                        'day8/stage1/',
                        'day8/stage2/',
                        'day8/stage3/',
                        'day8/stage4/',
                        'day8/stage5/',
                        'day8/stage6/',
                    ],
                },

                {
                    title: '第九天',
                    children: [
                        'day9/stage1/',
                        'day9/stage2/',
                        'day9/stage3/',
                        'day9/stage4/',
                        'day9/stage5/',
                        'day9/stage6/',
                    ],
                },

                {
                    title: '第十五天',
                    children: [
                        'day15/stage1/',
                        'day15/stage2/',
                        'day15/stage3/',
                        'day15/stage4/',
                        'day15/stage5/',
                        'day15/stage6/',
                        'day15/stage7/',
                        'day15/stage8/',
                    ],
                },

                {
                    title: '第十六天',
                    children: [
                        'day16/stage1/',
                        'day16/stage2/',
                        'day16/stage3/',
                        'day16/stage4/',
                        'day16/stage5/',
                        'day16/stage6/',
                    ],
                },

                {
                    title: '第十七天',
                    children: [
                        'day17/stage1/',
                        'day17/stage2/',
                        'day17/stage3/',
                        'day17/stage4/',
                        'day17/stage5/',
                        'day17/stage6/',
                        'day17/stage7/',
                    ],
                },

                {
                    title: '第十八天',
                    children: [
                        'day18/stage1/',
                        'day18/stage2/',
                        'day18/stage3/',
                        'day18/stage4/',
                        'day18/stage5/',
                        'day18/stage6/',
                    ],
                },

                {
                    title: '第十九天',
                    children: [
                        'day19/stage1/',
                        'day19/stage2/',
                        'day19/stage3/',
                        'day19/stage4/',
                        'day19/stage5/',
                        'day19/stage6/',
                    ],
                },
            ],
        },
    }
}