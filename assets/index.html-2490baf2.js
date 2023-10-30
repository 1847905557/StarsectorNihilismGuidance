import{_ as i,r as d,o as a,c as l,a as n,b as e,d as r,e as c}from"./app-78621c03.js";const o={},t=n("h1",{id:"熟悉-mod-文件夹",tabindex:"-1"},[n("a",{class:"header-anchor",href:"#熟悉-mod-文件夹","aria-hidden":"true"},"#"),e(" 熟悉 MOD 文件夹")],-1),v=n("p",null,[n("em",null,"本节预期阅读时间：~5 分钟。")],-1),u=n("p",null,[n("em",null,"本节预期操作时间：~3 分钟。")],-1),m=n("h2",{id:"快速获取-mod-文件夹",tabindex:"-1"},[n("a",{class:"header-anchor",href:"#快速获取-mod-文件夹","aria-hidden":"true"},"#"),e(" 快速获取 MOD 文件夹")],-1),b={href:"https://share.weiyun.com/xO3SkGzF",target:"_blank",rel:"noopener noreferrer"},h=c(`<h2 id="在其它操作之前" tabindex="-1"><a class="header-anchor" href="#在其它操作之前" aria-hidden="true">#</a> 在其它操作之前...</h2><p>您要做的第一件事是为您的 MOD 想好<strong>中文名称与英文名称</strong>，然后将英文名称调整为适合 <strong>MOD ID</strong> 的形式，并得出它的<strong>前缀名</strong>。</p><div class="custom-container tip"><p class="custom-container-title">TIP</p><p>假设您希望做一个叫<code>齿轮工业</code>的 MOD，那您可以翻译得出它的英文名是<code>Gear Industry</code>。</p><p><strong>MOD ID 不允许出现空格</strong>，而建议以下划线连接，但仅去除空格也行。因此您的 MOD ID 可以是<code>Gear_Industry</code>或<code>GearIndustry</code>。</p><p><strong>前缀名至少应该包括四个字母，并且以下划线结尾</strong>。因此您的前缀名是<code>Gear_</code>或<code>GearIn_</code>，也可以在 MOD ID 的基础上进一步自由发挥。</p></div><p>本教程的 MOD 范例名即为<code>齿轮工业</code>，MOD ID 为<code>GearIndustry</code>，前缀名为<code>GearIn_</code>。</p><h2 id="基础-mod-文件夹构成总览" tabindex="-1"><a class="header-anchor" href="#基础-mod-文件夹构成总览" aria-hidden="true">#</a> 基础 MOD 文件夹构成总览</h2><p>首先将<code>SampleMod</code>文件夹名改为您的 MOD ID，<strong>MOD 文件夹名应当保持永久不变</strong>，请注意不要在后方加入版本号。</p><p>然后是有些令人眼花缭乱的 MOD 文件夹内部：</p><div class="language-text line-numbers-mode" data-ext="text"><pre class="language-text"><code>.(MOD 文件夹本身)
├── data (一切数据相关)
│   ├──campaign
│   │   ├── terrain (进阶内容)
│   │   └── sim_opponents.csv
│   ├── config
│   │   ├── hull_styles.json (进阶内容)
│   │   ├── settings.json (进阶内容)
│   │   └── sounds.json (进阶内容)
│   ├── hullmods (舰船插件)
│   │   └── hullmods.csv
│   ├── hulls (舰船)
│   │   ├── ship_data.csv
│   │   └── wing_data.csv
│   ├── missions (战役)
│   │   └── mission_list.csv
│   ├── shipsystems (战术系统)
│   ├── strings (文本)
│   │   ├── description.csv (文本描述)
│   │   └── strings.json (进阶内容)
│   ├── trails (进阶内容)
│   ├── variants (装配文件)
│   ├── weapons (武器与弹体)
│   │   ├── proj
│   │   └── weapon_data.csv
│   └── world
│       └── faction (势力配置)
│           ├── default_ship_roles.json
│           └── factions.csv
├── graphics (一切贴图相关)
│   ├──backgrounds (星系背景)
│   ├── factions (势力旗帜)
│   ├── fx (进阶内容)
│   ├── hullmods (舰船插件)
│   ├── icons (进阶内容)
│   │   └── 各类子类
│   ├── illustrations (进阶内容)
│   ├── missiles (弹体/不仅仅是导弹)
│   ├── portraits (进阶内容)
│   ├── ships (舰船)
│   └── weapons (武器)
├── jars (进阶内容)
├── sounds (进阶内容)
├── src (进阶内容)
├── change_log.txt (更新日志)
└── mod_info.json (MOD INFO)
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div></div></div><h2 id="正式开始" tabindex="-1"><a class="header-anchor" href="#正式开始" aria-hidden="true">#</a> 正式开始</h2><p><strong>如果您觉得上述目录过于复杂，也不必担心，您现在需要关注的只有<code>mod_info.json</code></strong>。其它位置只有在介绍到对应模块时才会进行说明。</p><p>使用<code>VS Code</code>(此后默认所有文件都用它打开)打开<code>mod_info.json</code>，根据说明填入您的 MOD 信息，且不要随意改动没有说明的部分。然后将您的 MOD 当做一个普通的 MOD 一样放入您的游戏文件夹内的<code>mods</code>文件夹。</p><p>现在打开游戏，如果不出意外，那么您的 MOD 就已经能被识别了。</p>`,12);function p(_,g){const s=d("ExternalLinkIcon");return a(),l("div",null,[t,v,u,m,n("p",null,[e("您可以通过"),n("a",b,[e("这里"),r(s)]),e("下载一个已经组织好的 MOD 文件夹，省去了几乎所有麻烦。")]),h])}const f=i(o,[["render",p],["__file","index.html.vue"]]);export{f as default};
