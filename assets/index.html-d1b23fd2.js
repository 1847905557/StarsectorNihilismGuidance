import{_ as t,r as e,o as p,c,a as n,b as s,d as o,e as l}from"./app-78621c03.js";const i={},u=n("h1",{id:"modplugin-精确聚焦",tabindex:"-1"},[n("a",{class:"header-anchor",href:"#modplugin-精确聚焦","aria-hidden":"true"},"#"),s(" ModPlugin 精确聚焦")],-1),k=n("p",null,[n("em",null,"本节预期阅读时间：~5 分钟。")],-1),d=n("p",null,[n("em",null,"本节预期操作时间：~5 分钟。")],-1),r=n("h2",{id:"在一切之前",tabindex:"-1"},[n("a",{class:"header-anchor",href:"#在一切之前","aria-hidden":"true"},"#"),s(" 在一切之前...")],-1),v=n("strong",null,"再次强调",-1),m={href:"https://www.runoob.com/java/java-tutorial.html",target:"_blank",rel:"noopener noreferrer"},b=n("code",null,"基本 Java 语法",-1),g=n("code",null,"IDEA",-1),w=l(`<h2 id="重新审视-modplugin" tabindex="-1"><a class="header-anchor" href="#重新审视-modplugin" aria-hidden="true">#</a> 重新审视 ModPlugin</h2><p>ModPlugin 是目前接触地较为频繁的类，也是一个关键类。ModPlugin 应当继承了 BaseModPlugin，它的主要作用是<strong>为生涯模式的新建、存档、读档相关步骤插入新的自定义步骤，以及用于设置各类舰船、导弹、武器开火的默认 AI</strong>。通常来说，ModPlugin 不会频繁修改。</p><div class="language-java line-numbers-mode" data-ext="java"><pre class="language-java"><code>    <span class="token comment">// 几个必须解析且常用的方法</span>

	<span class="token comment">// 在游戏保存时调用</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">beforeGameSave</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>
		
	<span class="token punctuation">}</span>

	<span class="token comment">// 在游戏读档完毕时调用</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">onGameLoad</span><span class="token punctuation">(</span><span class="token keyword">boolean</span> newGame<span class="token punctuation">)</span> <span class="token punctuation">{</span>
		
	<span class="token punctuation">}</span>

	<span class="token comment">// 在远行星号这个程序刚启动时调用</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">onApplicationLoad</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token keyword">throws</span> <span class="token class-name">Exception</span> <span class="token punctuation">{</span>
		
	<span class="token punctuation">}</span>

	<span class="token comment">// 在新建一局生涯时调用</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">onNewGame</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>
		
	<span class="token punctuation">}</span>

	<span class="token comment">// 在此自定义舰船默认 AI，返回 null 时则为原版舰船 AI。注意传入的是 FleetMemberAPI</span>
	<span class="token keyword">public</span> <span class="token class-name">PluginPick</span><span class="token generics"><span class="token punctuation">&lt;</span><span class="token class-name">ShipAIPlugin</span><span class="token punctuation">&gt;</span></span> <span class="token function">pickShipAI</span><span class="token punctuation">(</span><span class="token class-name">FleetMemberAPI</span> member<span class="token punctuation">,</span> <span class="token class-name">ShipAPI</span> ship<span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">return</span> <span class="token keyword">null</span><span class="token punctuation">;</span>
	<span class="token punctuation">}</span>

	<span class="token comment">// 在此自定义武器开火默认 AI，返回 null 时则为原版武器开火 AI</span>
	<span class="token keyword">public</span> <span class="token class-name">PluginPick</span><span class="token generics"><span class="token punctuation">&lt;</span><span class="token class-name">AutofireAIPlugin</span><span class="token punctuation">&gt;</span></span> <span class="token function">pickWeaponAutofireAI</span><span class="token punctuation">(</span><span class="token class-name">WeaponAPI</span> weapon<span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">return</span> <span class="token keyword">null</span><span class="token punctuation">;</span>
	<span class="token punctuation">}</span>

	<span class="token comment">// 在此自定义导弹默认 AI，返回 null 时则为原版导弹 AI</span>
	<span class="token keyword">public</span> <span class="token class-name">PluginPick</span><span class="token generics"><span class="token punctuation">&lt;</span><span class="token class-name">MissileAIPlugin</span><span class="token punctuation">&gt;</span></span> <span class="token function">pickMissileAI</span><span class="token punctuation">(</span><span class="token class-name">MissileAPI</span> missile<span class="token punctuation">,</span>
			<span class="token class-name">ShipAPI</span> launchingShip<span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">return</span> <span class="token keyword">null</span><span class="token punctuation">;</span>
	<span class="token punctuation">}</span>

	<span class="token comment">// 其余方法请自行 ctrl+左键 检查</span>
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div></div></div><p>现在详细回顾<code>齿轮工业</code>的 ModPlugin 到底做了什么事情：</p><div class="language-java line-numbers-mode" data-ext="java"><pre class="language-java"><code><span class="token keyword">package</span> <span class="token namespace">data<span class="token punctuation">.</span>scripts</span><span class="token punctuation">;</span>

<span class="token keyword">import</span> <span class="token import"><span class="token namespace">com<span class="token punctuation">.</span>fs<span class="token punctuation">.</span>starfarer<span class="token punctuation">.</span>api<span class="token punctuation">.</span></span><span class="token class-name">BaseModPlugin</span></span><span class="token punctuation">;</span>
<span class="token keyword">import</span> <span class="token import"><span class="token namespace">com<span class="token punctuation">.</span>fs<span class="token punctuation">.</span>starfarer<span class="token punctuation">.</span>api<span class="token punctuation">.</span></span><span class="token class-name">Global</span></span><span class="token punctuation">;</span>
<span class="token keyword">import</span> <span class="token import"><span class="token namespace">com<span class="token punctuation">.</span>fs<span class="token punctuation">.</span>starfarer<span class="token punctuation">.</span>api<span class="token punctuation">.</span>impl<span class="token punctuation">.</span>campaign<span class="token punctuation">.</span>shared<span class="token punctuation">.</span></span><span class="token class-name">SharedData</span></span><span class="token punctuation">;</span>

<span class="token keyword">public</span> <span class="token keyword">class</span> <span class="token class-name">GearIn_ModPlugin</span> <span class="token keyword">extends</span> <span class="token class-name">BaseModPlugin</span> <span class="token punctuation">{</span>

	<span class="token annotation punctuation">@Override</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">onGameLoad</span><span class="token punctuation">(</span><span class="token keyword">boolean</span> newGame<span class="token punctuation">)</span> <span class="token punctuation">{</span>

	<span class="token punctuation">}</span>

	<span class="token annotation punctuation">@Override</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">beforeGameSave</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>

	<span class="token punctuation">}</span>

	<span class="token annotation punctuation">@Override</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">onApplicationLoad</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>

	<span class="token punctuation">}</span>
	
	<span class="token annotation punctuation">@Override</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">onNewGame</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>

		<span class="token keyword">if</span> <span class="token punctuation">(</span><span class="token function">NEX</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>

		<span class="token punctuation">}</span> <span class="token keyword">else</span> <span class="token punctuation">{</span>
			<span class="token comment">// 在此准备生成星系</span>

			<span class="token keyword">new</span> <span class="token class-name">GearIn_NormalGenerate</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">generate</span><span class="token punctuation">(</span><span class="token class-name">Global</span><span class="token punctuation">.</span><span class="token function">getSector</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">)</span><span class="token punctuation">;</span>
		<span class="token punctuation">}</span>

		<span class="token class-name">SharedData</span><span class="token punctuation">.</span><span class="token function">getData</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">getPersonBountyEventData</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">addParticipatingFaction</span><span class="token punctuation">(</span><span class="token string">&quot;gear_industry&quot;</span><span class="token punctuation">)</span><span class="token punctuation">;</span>
	<span class="token punctuation">}</span>

	<span class="token keyword">public</span> <span class="token keyword">static</span> <span class="token keyword">boolean</span> <span class="token function">NEX</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">return</span> <span class="token class-name">Global</span><span class="token punctuation">.</span><span class="token function">getSettings</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">getModManager</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">isModEnabled</span><span class="token punctuation">(</span><span class="token string">&quot;nexerelin&quot;</span><span class="token punctuation">)</span><span class="token punctuation">;</span>
	<span class="token punctuation">}</span>
<span class="token punctuation">}</span>
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div></div></div><p>很显然，它在 onNewGame 处添加了检测，当没有开启势力争霸时生成星系。然后用 SharedData 将势力注册到支持赏金机制的列表中。这是一个非常原始且简单的例子，但随着后续的开发，ModPlugin 将变得越来越复杂。</p><h2 id="完全支持势力争霸" tabindex="-1"><a class="header-anchor" href="#完全支持势力争霸" aria-hidden="true">#</a> 完全支持势力争霸</h2><p>不过既然都以势力争霸作为例子了，那么就直接在此做它的完全支持。在前文 前缀名_NormalGenerate 的同目录下新建 前缀名_NEXGenerate，然后导入：</p><div class="language-java line-numbers-mode" data-ext="java"><pre class="language-java"><code><span class="token keyword">package</span> <span class="token namespace">data<span class="token punctuation">.</span>scripts</span><span class="token punctuation">;</span>

<span class="token keyword">import</span> <span class="token import"><span class="token namespace">com<span class="token punctuation">.</span>fs<span class="token punctuation">.</span>starfarer<span class="token punctuation">.</span>api<span class="token punctuation">.</span>campaign<span class="token punctuation">.</span></span><span class="token class-name">SectorAPI</span></span><span class="token punctuation">;</span>
<span class="token keyword">import</span> <span class="token import"><span class="token namespace">exerelin<span class="token punctuation">.</span>campaign<span class="token punctuation">.</span></span><span class="token class-name">SectorManager</span></span><span class="token punctuation">;</span>

<span class="token keyword">public</span> <span class="token keyword">class</span> <span class="token class-name">GearIn_NEXGenerate</span> <span class="token keyword">extends</span> <span class="token class-name">PLSP_NormalGenerate</span> <span class="token punctuation">{</span>
	
	<span class="token annotation punctuation">@Override</span>
	<span class="token keyword">public</span> <span class="token keyword">void</span> <span class="token function">generate</span><span class="token punctuation">(</span><span class="token class-name">SectorAPI</span> sector<span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">if</span> <span class="token punctuation">(</span><span class="token class-name">SectorManager</span><span class="token punctuation">.</span><span class="token function">getManager</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">isCorvusMode</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">)</span> <span class="token punctuation">{</span> <span class="token comment">// 检测 nex 是否为原版地图模式</span>
			<span class="token keyword">super</span><span class="token punctuation">.</span><span class="token function">generate</span><span class="token punctuation">(</span>sector<span class="token punctuation">)</span><span class="token punctuation">;</span>
		<span class="token punctuation">}</span>
	<span class="token punctuation">}</span>
<span class="token punctuation">}</span>
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div></div></div><p>并修改 ModPlugin：</p><div class="language-java line-numbers-mode" data-ext="java"><pre class="language-java"><code>			<span class="token keyword">if</span> <span class="token punctuation">(</span><span class="token function">NEX</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>
				<span class="token keyword">new</span> <span class="token class-name">GearIn_NEXGenerate</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">generate</span><span class="token punctuation">(</span><span class="token class-name">Global</span><span class="token punctuation">.</span><span class="token function">getSector</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">)</span><span class="token punctuation">;</span>
			<span class="token punctuation">}</span> <span class="token keyword">else</span> <span class="token punctuation">{</span>
				<span class="token keyword">new</span> <span class="token class-name">GearIn_NormalGenerate</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">generate</span><span class="token punctuation">(</span><span class="token class-name">Global</span><span class="token punctuation">.</span><span class="token function">getSector</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">)</span><span class="token punctuation">;</span>
			<span class="token punctuation">}</span>
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div></div></div><p>即可完成完整的势力争霸支持。</p>`,12);function f(y,h){const a=e("ExternalLinkIcon");return p(),c("div",null,[u,k,d,r,n("p",null,[v,s("，编者默认您已经在"),n("a",m,[s("此处"),o(a)]),s("或类似的地方学习了"),b,s("并安装了一种叫"),g,s("的工具，且包括编译、封装等基础操作。编者将默认您对于各类 Java 基础概念均拥有一定程度上的理解，并不再提及诸如类名调整等简单修改。如果您并不了解上述内容，请去学习它。")]),w])}const P=t(i,[["render",f],["__file","index.html.vue"]]);export{P as default};
