import{_ as n,o as s,c as a,e}from"./app-78621c03.js";const t={},p=e(`<h1 id="global-global-衍生-settings-精确聚焦" tabindex="-1"><a class="header-anchor" href="#global-global-衍生-settings-精确聚焦" aria-hidden="true">#</a> Global/Global 衍生/settings 精确聚焦</h1><p><em>本节预期阅读时间：~10 分钟。</em></p><p><em>本节预期操作时间：~10 分钟。</em></p><h2 id="global" tabindex="-1"><a class="header-anchor" href="#global" aria-hidden="true">#</a> Global</h2><p>Global 同样也是关键类，且日后将以极高频率出现在您的 import 中。它主要的作用是<strong>提供几个最关键的静态方法，允许对 CombatEngineAPI/SectorAPI/SettingsAPI/SoundPlayerAPI/Logger 进行访问</strong>，可以将其理解为一种重要的中枢。而本章则将对其中的几个简单类进行介绍。</p><div class="language-java line-numbers-mode" data-ext="java"><pre class="language-java"><code>	<span class="token comment">// 日后会频繁使用的几个关键 getter</span>
	<span class="token comment">// getFactory 可以无视，它没什么用</span>

	<span class="token keyword">public</span> <span class="token keyword">static</span> <span class="token class-name">Logger</span> <span class="token function">getLogger</span><span class="token punctuation">(</span><span class="token class-name">Class</span> c<span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token class-name">Logger</span> log <span class="token operator">=</span> <span class="token class-name">Logger</span><span class="token punctuation">.</span><span class="token function">getLogger</span><span class="token punctuation">(</span>c<span class="token punctuation">)</span><span class="token punctuation">;</span>
		<span class="token keyword">return</span> log<span class="token punctuation">;</span>
	<span class="token punctuation">}</span>

	<span class="token keyword">public</span> <span class="token keyword">static</span> <span class="token class-name">SoundPlayerAPI</span> <span class="token function">getSoundPlayer</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">return</span> soundPlayer<span class="token punctuation">;</span>
	<span class="token punctuation">}</span>

	<span class="token keyword">public</span> <span class="token keyword">static</span> <span class="token class-name">SettingsAPI</span> <span class="token function">getSettings</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">return</span> settingsAPI<span class="token punctuation">;</span>
	<span class="token punctuation">}</span>
	
	<span class="token keyword">public</span> <span class="token keyword">static</span> <span class="token class-name">SectorAPI</span> <span class="token function">getSector</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">return</span> sectorAPI<span class="token punctuation">;</span>
	<span class="token punctuation">}</span>
	
	<span class="token keyword">public</span> <span class="token keyword">static</span> <span class="token class-name">CombatEngineAPI</span> <span class="token function">getCombatEngine</span><span class="token punctuation">(</span><span class="token punctuation">)</span> <span class="token punctuation">{</span>
		<span class="token keyword">return</span> combatEngine<span class="token punctuation">;</span>
	<span class="token punctuation">}</span>
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div></div></div><h2 id="logger" tabindex="-1"><a class="header-anchor" href="#logger" aria-hidden="true">#</a> Logger</h2><p>Logger 用于在 log 中输出任意一行消息，通常被用于日志记录，也可以用来调试以及检查某些变量的数值是否符合预期。您最常用的形式可能看上去是这样的：</p><div class="language-java line-numbers-mode" data-ext="java"><pre class="language-java"><code><span class="token class-name">Global</span><span class="token punctuation">.</span><span class="token function">getLogger</span><span class="token punctuation">(</span><span class="token keyword">this</span><span class="token punctuation">.</span><span class="token function">getClass</span><span class="token punctuation">(</span><span class="token punctuation">)</span><span class="token punctuation">)</span><span class="token punctuation">.</span><span class="token function">info</span><span class="token punctuation">(</span><span class="token string">&quot;任意内容&quot;</span><span class="token punctuation">)</span><span class="token punctuation">;</span>
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div></div></div><p>任何时间，任何地点，传入类，开箱即用。</p><h2 id="soundplayerapi" tabindex="-1"><a class="header-anchor" href="#soundplayerapi" aria-hidden="true">#</a> SoundPlayerAPI</h2><p>SoundPlayerAPI 用于播放音效、UI 音效和音乐，以及控制它们的暂停、获取状态等。这个 API 并不是很好介绍，因为<strong>它是少见的带有较完整文档的 API</strong>，且所有常用方法都带有文档。</p><div class="language-java line-numbers-mode" data-ext="java"><pre class="language-java"><code>	<span class="token comment">// 最常用的三个方法</span>

	<span class="token doc-comment comment">/**
	 * UI sound files should be stereo.
	 * <span class="token keyword">@param</span> <span class="token parameter">id</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">pitch</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">volume</span>
	 */</span>
	<span class="token class-name">SoundAPI</span> <span class="token function">playUISound</span><span class="token punctuation">(</span><span class="token class-name">String</span> id<span class="token punctuation">,</span> <span class="token keyword">float</span> pitch<span class="token punctuation">,</span> <span class="token keyword">float</span> volume<span class="token punctuation">)</span><span class="token punctuation">;</span>
	
	<span class="token doc-comment comment">/**
	 * Sound file must be *mono* for it to be properly played within the engine.
	 * <span class="token keyword">@param</span> <span class="token parameter">id</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">pitch</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">volume</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">loc</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">vel</span>
	 * <span class="token keyword">@return</span>
	 */</span>
	<span class="token class-name">SoundAPI</span> <span class="token function">playSound</span><span class="token punctuation">(</span><span class="token class-name">String</span> id<span class="token punctuation">,</span> <span class="token keyword">float</span> pitch<span class="token punctuation">,</span> <span class="token keyword">float</span> volume<span class="token punctuation">,</span> <span class="token class-name">Vector2f</span> loc<span class="token punctuation">,</span> <span class="token class-name">Vector2f</span> vel<span class="token punctuation">)</span><span class="token punctuation">;</span>
	
	<span class="token doc-comment comment">/**
	 * Loop a sound. Must be called every frame or the loop will fade out. Should be mono.
	 * 
	 * <span class="token keyword">@param</span> <span class="token parameter">id</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">playingEntity</span> Required. Used to help uniquely identify playing loops. Also used to figure out which loops to not play (i.e., same entity playing multiples of the same loop would only play the one.)
	 * <span class="token keyword">@param</span> <span class="token parameter">pitch</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">volume</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">loc</span>
	 * <span class="token keyword">@param</span> <span class="token parameter">vel</span>
	 */</span>
	<span class="token keyword">void</span> <span class="token function">playLoop</span><span class="token punctuation">(</span><span class="token class-name">String</span> id<span class="token punctuation">,</span> <span class="token class-name">Object</span> playingEntity<span class="token punctuation">,</span> <span class="token keyword">float</span> pitch<span class="token punctuation">,</span> <span class="token keyword">float</span> volume<span class="token punctuation">,</span> <span class="token class-name">Vector2f</span> loc<span class="token punctuation">,</span> <span class="token class-name">Vector2f</span> vel<span class="token punctuation">)</span><span class="token punctuation">;</span>

	<span class="token comment">// 例如 Global.getSoundPlayer().playSound(各类参数);</span>
	<span class="token comment">// 其余不常用方法请自行 ctrl+左键</span>
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div></div></div><p>playUISound 和 playSound 有所不同，<strong>前者只支持双声道音效而后者只支持单声道音效</strong>，前者没有播放点而后者则是在 loc 的位置播放并根据方位和距离计算具体声音。vel 不可为 null。</p><h2 id="settingsapi" tabindex="-1"><a class="header-anchor" href="#settingsapi" aria-hidden="true">#</a> SettingsAPI</h2>`,15),l=[p];function o(c,i){return s(),a("div",null,l)}const d=n(t,[["render",o],["__file","index.html.vue"]]);export{d as default};
