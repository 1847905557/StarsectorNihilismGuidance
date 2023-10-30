import{_ as n,o as s,c as a,e as t}from"./app-78621c03.js";const p="/StarsectorNihilismGuidance/assets/faction-40e9c196.png",o={},e=t(`<h1 id="制作新常规势力" tabindex="-1"><a class="header-anchor" href="#制作新常规势力" aria-hidden="true">#</a> 制作新常规势力</h1><p><em>本节预期阅读时间：~15 分钟。</em></p><p><em>本节预期操作时间：~10 分钟。</em></p><h2 id="制作新势力文件" tabindex="-1"><a class="header-anchor" href="#制作新势力文件" aria-hidden="true">#</a> 制作新势力文件</h2><p>编者相信您应该已经猜到编者接下来要干什么了。</p><p>从<code>data/world/factions</code>文件夹中复制<code>hegemony.faction</code>文件到 MOD 的同路径下，并更名为<code>gear_industry.faction</code>。在本例中，<code>齿轮工业</code>将以<code>霸主</code>为基础修改而来。打开文件，准备进行修改：</p><div class="language-json line-numbers-mode" data-ext="json"><pre class="language-json"><code><span class="token punctuation">{</span>
	id<span class="token operator">:</span><span class="token string">&quot;hegemony&quot;</span><span class="token punctuation">,</span> # 势力的id
	<span class="token property">&quot;color&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token number">245</span><span class="token punctuation">,</span><span class="token number">150</span><span class="token punctuation">,</span><span class="token number">30</span><span class="token punctuation">,</span><span class="token number">255</span><span class="token punctuation">]</span><span class="token punctuation">,</span> # 势力的颜色
	<span class="token property">&quot;displayName&quot;</span><span class="token operator">:</span><span class="token string">&quot;Hegemony&quot;</span><span class="token punctuation">,</span> # 势力的名称
	<span class="token property">&quot;displayNameWithArticle&quot;</span><span class="token operator">:</span><span class="token string">&quot;the Hegemony&quot;</span><span class="token punctuation">,</span> # 势力的名称，在中文环境下，应当与 displayName 一致
	<span class="token property">&quot;logo&quot;</span><span class="token operator">:</span><span class="token string">&quot;graphics/factions/hegemony.png&quot;</span><span class="token punctuation">,</span> # 势力的旗帜贴图路径，贴图大小应该是 <span class="token number">410</span>*<span class="token number">256</span>
	<span class="token property">&quot;crest&quot;</span><span class="token operator">:</span><span class="token string">&quot;graphics/factions/crest_hegemony.png&quot;</span><span class="token punctuation">,</span> # 势力的短旗帜贴图路径，贴图大小应该是 <span class="token number">256</span>*<span class="token number">256</span>
	<span class="token property">&quot;shipNamePrefix&quot;</span><span class="token operator">:</span><span class="token string">&quot;HSS&quot;</span><span class="token punctuation">,</span> # 势力的舰船前缀，大写英文字母，可随意编写
	<span class="token property">&quot;shipNameSources&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的舰船名词典，详见 starsector-core/data/strings/ship_names
		<span class="token property">&quot;HEGEMONY&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;BRITISH_NAVY&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;ROMAN&quot;</span><span class="token operator">:</span><span class="token number">2</span><span class="token punctuation">,</span>
		<span class="token property">&quot;GREEK&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;SPACE&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;GENERAL&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;names&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的人名词典，详见 starsector-core/data/characters
		<span class="token property">&quot;old english&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;modern&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;world&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;future&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;myth&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	
	# variantOverrides restricts hulls to listed variants and adjusts their probability
	<span class="token property">&quot;variantOverrides&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	
	# multiplier for how often hulls show up in the faction&#39;s fleets
	<span class="token property">&quot;hullFrequency&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的舰船出现概率调整
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span>
			<span class="token property">&quot;heg_aux_bp&quot;</span><span class="token operator">:</span><span class="token number">2</span><span class="token punctuation">,</span> # 船体 ID <span class="token operator">:</span> 出现倍率
			<span class="token property">&quot;XIV_bp&quot;</span><span class="token operator">:</span><span class="token number">0.25</span><span class="token punctuation">,</span>
		<span class="token punctuation">}</span><span class="token punctuation">,</span>
		<span class="token property">&quot;hulls&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span>
			<span class="token property">&quot;buffalo2&quot;</span><span class="token operator">:</span><span class="token number">0</span><span class="token punctuation">,</span> # 出现倍率设为 <span class="token number">0</span> 则为不出现
		<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	# ships the faction gets access to when importing S&amp;W out-of-faction
	<span class="token property">&quot;shipsWhenImporting&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的持入舰船
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token string">&quot;base_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;heg_aux_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;lowtech_bp&quot;</span><span class="token punctuation">]</span><span class="token punctuation">,</span> # 请尤其注意这个 tags 的内容
		<span class="token property">&quot;hulls&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;knownShips&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的舰船列表
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token string">&quot;base_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;heg_aux_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;lowtech_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;midline_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;XIV_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;hegemony&quot;</span><span class="token punctuation">]</span><span class="token punctuation">,</span> # 请尤其注意这个 tags 的内容
		<span class="token property">&quot;hulls&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span>
			<span class="token string">&quot;atlas&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;monitor&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;prometheus&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;legion&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;onslaught&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;gryphon&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;eagle&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;valkyrie&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;vanguard&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;manticore&quot;</span><span class="token punctuation">,</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	# listing ships here will make the faction mostly use them even if other hulls become available
	<span class="token property">&quot;priorityShips&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 优先选用的舰船，会显著覆盖本势力其它舰船的出现率，如无特殊要求则留空
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token punctuation">]</span><span class="token punctuation">,</span>
		<span class="token property">&quot;hulls&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span>
			<span class="token string">&quot;condor&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;mora&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;legion&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;onslaught&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;onslaught_xiv&quot;</span><span class="token punctuation">,</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;knownFighters&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的战机列表
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token string">&quot;base_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;lowtech_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;midline_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;hegemony&quot;</span><span class="token punctuation">]</span><span class="token punctuation">,</span> # 请尤其注意这个 tags 的内容
		<span class="token property">&quot;fighters&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span>
			<span class="token string">&quot;thunder_wing&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;hoplon_wing&quot;</span><span class="token punctuation">,</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;priorityFighters&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 优先选用的战机，会显著覆盖本势力其它战机的出现率，如无特殊要求则留空
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token punctuation">]</span><span class="token punctuation">,</span>
		<span class="token property">&quot;fighters&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;knownWeapons&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的武器列表
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token string">&quot;base_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;lowtech_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;midline_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;hightech_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;missile_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;hegemony&quot;</span><span class="token punctuation">]</span><span class="token punctuation">,</span> # 请尤其注意这个 tags 的内容
		<span class="token property">&quot;weapons&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span>
			<span class="token string">&quot;railgun&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;dualflak&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;heavymauler&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;hveldriver&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;gauss&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;mjolnir&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;breachpod&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;sabotpod&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;phasecl&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;cyclone&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;hurricane&quot;</span><span class="token punctuation">,</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;priorityWeapons&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 优先选用的武器，会显著覆盖本势力其它武器的出现率，如无特殊要求则留空
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token punctuation">]</span><span class="token punctuation">,</span>
		<span class="token property">&quot;weapons&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;knownHullMods&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的舰船插件列表
		<span class="token property">&quot;tags&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span><span class="token string">&quot;base_bp&quot;</span><span class="token punctuation">,</span> <span class="token string">&quot;hegemony&quot;</span><span class="token punctuation">]</span><span class="token punctuation">,</span> # 请尤其注意这个 tags 的内容
		<span class="token property">&quot;hullMods&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span>
			<span class="token string">&quot;advancedshieldemitter&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;turretgyros&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;armoredweapons&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;augmentedengines&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;autorepair&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;expanded_deck_crew&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;magazines&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;missleracks&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;extendedshieldemitter&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;frontemitter&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;frontshield&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;heavyarmor&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;insulatedengine&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;targetingunit&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;nav_relay&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;operations_center&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;fluxbreakers&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;stabilizedshieldemitter&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;frontshield&quot;</span><span class="token punctuation">,</span>  # makeshift
			<span class="token string">&quot;surveying_equipment&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;efficiency_overhaul&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;converted_fighterbay&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;ballistic_rangefinder&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;shield_shunt&quot;</span><span class="token punctuation">,</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;factionDoctrine&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力的舰队学说设置，与游戏内玩家自创势力相同
        # 下列三条的总合建议为 <span class="token number">7</span>，不应该低于 <span class="token number">1</span>
		<span class="token property">&quot;warships&quot;</span><span class="token operator">:</span><span class="token number">4</span><span class="token punctuation">,</span> # 通常战舰占比
		<span class="token property">&quot;carriers&quot;</span><span class="token operator">:</span><span class="token number">2</span><span class="token punctuation">,</span> # 航母占比
		<span class="token property">&quot;phaseShips&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span> # 相位舰占比
		
        # 下列三条的总合建议为 <span class="token number">7</span>，不应该低于 <span class="token number">1</span>
		<span class="token property">&quot;officerQuality&quot;</span><span class="token operator">:</span><span class="token number">5</span><span class="token punctuation">,</span> # 军官质量(影响等级、精英技能数)加成
		<span class="token property">&quot;shipQuality&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span> # 舰船质量(影响D插比率)加成
		<span class="token property">&quot;numShips&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span> # 舰船数量加成
		
		<span class="token property">&quot;shipSize&quot;</span><span class="token operator">:</span><span class="token number">4</span><span class="token punctuation">,</span> # 舰船大小倾向，越大越倾向于使用战列舰，最大为 <span class="token number">5</span>，不应该低于 <span class="token number">1</span>
		
		<span class="token property">&quot;aggression&quot;</span><span class="token operator">:</span><span class="token number">2</span><span class="token punctuation">,</span> # 军官性格倾向，越大越倾向于全鲁莽，最大为 <span class="token number">5</span>，不应该低于 <span class="token number">1</span>
		
		<span class="token property">&quot;combatFreighterProbability&quot;</span><span class="token operator">:</span><span class="token number">0.1</span><span class="token punctuation">,</span> # 用武装货舰替代正常货舰的概率
		<span class="token property">&quot;combatFreighterCombatUseFraction&quot;</span><span class="token operator">:</span><span class="token number">0</span><span class="token punctuation">,</span> # 当舰队损失至低于此比率时，才将武装货舰当正规战舰上场
		<span class="token property">&quot;combatFreighterCombatUseFractionWhenPriority&quot;</span><span class="token operator">:</span><span class="token number">0</span><span class="token punctuation">,</span> # 用武装货舰当正规战舰上场的概率(哪怕自己不缺的正规战舰)
		<span class="token property">&quot;autofitRandomizeProbability&quot;</span><span class="token operator">:</span><span class="token number">0.1</span><span class="token punctuation">,</span> # 自动装配时的武器、插件打乱程度
		
		<span class="token property">&quot;commanderSkillsShuffleProbability&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span>
		<span class="token property">&quot;commanderSkills&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span> # 本势力允许的指挥官舰队技能
			<span class="token string">&quot;crew_training&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;officer_training&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;coordinated_maneuvers&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;support_doctrine&quot;</span><span class="token punctuation">,</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;illegalCommodities&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span> # 违禁品，在data/campaign/commodity.csv
		<span class="token string">&quot;drugs&quot;</span><span class="token punctuation">,</span> # 非常不建议干出例如把重型武器或者补给作为违禁品的事情
		<span class="token string">&quot;organs&quot;</span><span class="token punctuation">,</span>
		<span class="token string">&quot;hand_weapons&quot;</span><span class="token punctuation">,</span>
		<span class="token string">&quot;ai_cores&quot;</span><span class="token punctuation">,</span>	
	<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token property">&quot;music&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 音乐
		<span class="token property">&quot;theme&quot;</span><span class="token operator">:</span><span class="token string">&quot;music_hegemony_market_neutral&quot;</span><span class="token punctuation">,</span>
		<span class="token property">&quot;market_neutral&quot;</span><span class="token operator">:</span><span class="token string">&quot;music_hegemony_market_neutral&quot;</span><span class="token punctuation">,</span>
		<span class="token property">&quot;market_hostile&quot;</span><span class="token operator">:</span><span class="token string">&quot;music_hegemony_market_hostile&quot;</span><span class="token punctuation">,</span>
		<span class="token property">&quot;market_friendly&quot;</span><span class="token operator">:</span><span class="token string">&quot;music_hegemony_market_friendly&quot;</span><span class="token punctuation">,</span>
		<span class="token property">&quot;encounter_neutral&quot;</span><span class="token operator">:</span><span class="token string">&quot;music_hegemony_encounter_neutral&quot;</span><span class="token punctuation">,</span>
		<span class="token property">&quot;encounter_hostile&quot;</span><span class="token operator">:</span><span class="token string">&quot;music_hegemony_encounter_hostile&quot;</span><span class="token punctuation">,</span>
		<span class="token property">&quot;encounter_friendly&quot;</span><span class="token operator">:</span><span class="token string">&quot;music_hegemony_encounter_friendly&quot;</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>	
	<span class="token property">&quot;internalComms&quot;</span><span class="token operator">:</span><span class="token string">&quot;hegemony_internal&quot;</span><span class="token punctuation">,</span>
	#<span class="token property">&quot;tariffFraction&quot;</span><span class="token operator">:</span><span class="token number">0.4</span><span class="token punctuation">,</span> # 去掉最前方的 # 启用，税率，非常建议无视
	#<span class="token property">&quot;tollFraction&quot;</span><span class="token operator">:</span><span class="token number">0.1</span><span class="token punctuation">,</span> # 去掉最前方的 # 启用，扫描玩家货舱后损毁CR的运算参数之一，非常建议无视
	#<span class="token property">&quot;fineFraction&quot;</span><span class="token operator">:</span><span class="token number">0.25</span><span class="token punctuation">,</span> # 去掉最前方的 # 启用，扫描玩家货舱后损毁CR的运算参数之一，非常建议无视
	<span class="token property">&quot;portraits&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span>
		<span class="token property">&quot;standard_male&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span> # 头像，男性
			<span class="token string">&quot;graphics/portraits/portrait_hegemony01.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony02.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony05.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony07.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony09.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony12.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony13.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony15.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_mercenary03.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_mercenary06.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_mercenary08.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait15.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait33.png&quot;</span><span class="token punctuation">,</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
		<span class="token property">&quot;standard_female&quot;</span><span class="token operator">:</span><span class="token punctuation">[</span> # 头像，女性
			<span class="token string">&quot;graphics/portraits/portrait_hegemony03.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony04.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony08.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony10.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony11.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony14.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait_hegemony16.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait16.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait22.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait34.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait37.png&quot;</span><span class="token punctuation">,</span>
			<span class="token string">&quot;graphics/portraits/portrait42.png&quot;</span><span class="token punctuation">,</span>
		<span class="token punctuation">]</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;ranks&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span>
		<span class="token property">&quot;ranks&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 各类 rank 的自定义名称，若不需要可以整行删去，删去则为使用原版的默认名
			<span class="token property">&quot;factionLeader&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span><span class="token property">&quot;name&quot;</span><span class="token operator">:</span><span class="token string">&quot;High Hegemon&quot;</span><span class="token punctuation">}</span><span class="token punctuation">,</span>
			<span class="token property">&quot;spaceCommander&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span><span class="token property">&quot;name&quot;</span><span class="token operator">:</span><span class="token string">&quot;Commander&quot;</span><span class="token punctuation">}</span><span class="token punctuation">,</span>
		<span class="token punctuation">}</span><span class="token punctuation">,</span>
		<span class="token property">&quot;posts&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span>
			<span class="token property">&quot;factionLeader&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span><span class="token property">&quot;name&quot;</span><span class="token operator">:</span><span class="token string">&quot;High Hegemon&quot;</span><span class="token punctuation">}</span><span class="token punctuation">,</span>
			<span class="token property">&quot;patrolCommander&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span><span class="token property">&quot;name&quot;</span><span class="token operator">:</span><span class="token string">&quot;Patrol Commander&quot;</span><span class="token punctuation">}</span><span class="token punctuation">,</span>
			<span class="token property">&quot;fleetCommander&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span><span class="token property">&quot;name&quot;</span><span class="token operator">:</span><span class="token string">&quot;Fleet Commander&quot;</span><span class="token punctuation">}</span><span class="token punctuation">,</span>
			<span class="token property">&quot;baseCommander&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span><span class="token property">&quot;name&quot;</span><span class="token operator">:</span><span class="token string">&quot;Base Commander&quot;</span><span class="token punctuation">}</span><span class="token punctuation">,</span>
		<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token property">&quot;voices&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 势力产生的联络人的类型分配比率，根据低、中、高重要性可以各自不同设计
		<span class="token property">&quot;LOW&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 低重要性
			<span class="token property">&quot;soldier&quot;</span><span class="token operator">:</span><span class="token number">10</span><span class="token punctuation">,</span> # 类型<span class="token operator">:</span>权重
			<span class="token property">&quot;faithful&quot;</span><span class="token operator">:</span><span class="token number">5</span><span class="token punctuation">,</span>		
			<span class="token property">&quot;spacer&quot;</span><span class="token operator">:</span><span class="token number">5</span><span class="token punctuation">,</span>
		<span class="token punctuation">}</span><span class="token punctuation">,</span>
		<span class="token property">&quot;MEDIUM&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 中重要性
			<span class="token property">&quot;soldier&quot;</span><span class="token operator">:</span><span class="token number">10</span><span class="token punctuation">,</span>
			<span class="token property">&quot;official&quot;</span><span class="token operator">:</span><span class="token number">5</span><span class="token punctuation">,</span>
		<span class="token punctuation">}</span><span class="token punctuation">,</span>
		<span class="token property">&quot;HIGH&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span> # 高重要性
			<span class="token property">&quot;soldier&quot;</span><span class="token operator">:</span><span class="token number">5</span><span class="token punctuation">,</span>
			<span class="token property">&quot;official&quot;</span><span class="token operator">:</span><span class="token number">10</span><span class="token punctuation">,</span>
			<span class="token property">&quot;aristo&quot;</span><span class="token operator">:</span><span class="token number">3</span><span class="token punctuation">,</span>
		<span class="token punctuation">}</span><span class="token punctuation">,</span>
		# 所有可用的 voice(类型) 有
		# spacer 寻常船员
		# faithful 卢德信徒
		# pather 左径狂信徒
		# business 星际商人
		# official 政务官员
		# soldier 下级士兵
		# scientist 科学家
		# villain 海盗军阀
		# aristo 旧式贵族
	<span class="token property">&quot;custom&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span>
		<span class="token property">&quot;offersCommissions&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否发布雇佣协议
		<span class="token property">&quot;engagesInHostilities&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否敌对时主动攻击
		<span class="token property">&quot;buysAICores&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否收购ai核心
		<span class="token property">&quot;AICoreValueMult&quot;</span><span class="token operator">:</span><span class="token number">1</span><span class="token punctuation">,</span> # 收购ai核心的价格倍率，<span class="token number">1</span>就是默认，<span class="token number">2</span>就是翻倍，允许小数
		<span class="token property">&quot;AICoreRepMult&quot;</span><span class="token operator">:</span><span class="token number">2</span><span class="token punctuation">,</span> # 收购ai核心的关系倍率，<span class="token number">1</span>就是默认，<span class="token number">2</span>就是翻倍，允许小数
		<span class="token property">&quot;buysSurveyData&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否会发布调查数据任务
		<span class="token property">&quot;hostilityImpactOnGrowth&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 无效果，无视之
		<span class="token property">&quot;caresAboutAtrocities&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否对饱和轰炸进行谴责
        
        <span class="token property">&quot;engageWhenEvenStrength&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否在敌人比较强时也主动触发舰队战斗
		<span class="token property">&quot;decentralized&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 去中心化，让该势力相关的某些赏金任务更不容易发现玩家的身份，且影响某些内核代码判定。诸如自由联盟、海盗的势力应当设为 <span class="token boolean">true</span>
		<span class="token property">&quot;gaVIPMission&quot;</span><span class="token operator">:</span><span class="token boolean">false</span><span class="token punctuation">,</span> # 有 Galatia 学院的 VIP 任务，极度极度不建议设为 <span class="token boolean">true</span>
		<span class="token property">&quot;postsNoBounties&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 如果设为 <span class="token boolean">true</span>，则不主动发布势力赏金
		<span class="token property">&quot;offerMissionsWhenHostile&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否在和玩家敌对时也对玩家发布任务
		<span class="token property">&quot;allowsTransponderOffTrade&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否允许关闭应答器交易
		<span class="token property">&quot;patrolsHaveNoPatrolMemoryKey&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 如果设为 <span class="token boolean">true</span>，则该势力的巡逻队在代码机制上不被认为是巡逻队
		<span class="token property">&quot;pirateBehavior&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否是海盗型势力
		<span class="token property">&quot;makesPirateBases&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否会虚空创造基地，如同海盗那样
		<span class="token property">&quot;fightToTheLast&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否在战斗中大多数船都会死战不退
        
		<span class="token property">&quot;punitiveExpeditionData&quot;</span><span class="token operator">:</span><span class="token punctuation">{</span>
			<span class="token property">&quot;vsCompetitors&quot;</span><span class="token operator">:</span><span class="token boolean">false</span><span class="token punctuation">,</span> # 是否对商业竞争发动远征
			<span class="token property">&quot;vsFreePort&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否对自由港发动远征
			<span class="token property">&quot;canBombard&quot;</span><span class="token operator">:</span><span class="token boolean">false</span><span class="token punctuation">,</span> # 能否在发动远征时顺带进行轰炸
			<span class="token property">&quot;territorial&quot;</span><span class="token operator">:</span><span class="token boolean">true</span><span class="token punctuation">,</span> # 是否对星系内的其它势力获得额外远征机会
		<span class="token punctuation">}</span><span class="token punctuation">,</span>
	<span class="token punctuation">}</span><span class="token punctuation">,</span>	
	
	
<span class="token punctuation">}</span><span class="token punctuation">,</span>
</code></pre><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div><div class="line-number"></div></div></div><p>由于 faction 型文件实在过长，不便于以表格形式列出，因此编者以注释形式注解了所有有意义且适合初学者调整的项目，您可以因情况自行修改。</p><div class="custom-container tip"><p class="custom-container-title">bp 的作用</p><p>编者曾建议您为您制作的舰船、战机、武器填写一些似乎意义不明的<code>前缀名_bp</code>，在本例为<code>GearIn_bp</code>。</p><p>现在它们将产生作用。您只需要在<code>known_*</code>对应的<code>tags</code>内写上您的<code>前缀名_bp</code>，即可自动导入势力舰船、战机、武器。</p></div><div class="custom-container tip"><p class="custom-container-title">为势力安排武器和战机</p><p>您未必能脱离原版武器库与战机库来形成您势力的军备，因此不建议删除所有武器方面的导入。</p></div><p>最后在同目录下的<code>factions.csv</code>中注册势力，别忘了删除前方的<code>#</code>号：</p><div class="language-csv line-numbers-mode" data-ext="csv"><pre class="language-csv"><code><span class="token value">faction</span>
<span class="token value">data/world/factions/gear_idustry.faction</span>
</code></pre><div class="highlight-lines"><br><div class="highlight-line"> </div></div><div class="line-numbers" aria-hidden="true"><div class="line-number"></div><div class="line-number"></div></div></div><p><img src="`+p+'" alt=""></p><p>它已经大致完成了，但还需要一些微小的工作。</p><h3 id="将装配文件导入生涯" tabindex="-1"><a class="header-anchor" href="#将装配文件导入生涯" aria-hidden="true">#</a> 将装配文件导入生涯</h3><p>同目录下还有一个<code>default_ship_roles.json</code>文件，它是用于将装配导入生涯的。如果没有设置这个文件，那么您的势力将无法调用您在上一阶段制作的舰船装配。<strong>打开<code>default_ship_roles.json</code>，参考原版同路径下的文件进行修改</strong>。</p><h2 id="实用势力包-bp-与其它生成型-tags-速查表" tabindex="-1"><a class="header-anchor" href="#实用势力包-bp-与其它生成型-tags-速查表" aria-hidden="true">#</a> 实用势力包(bp)与其它生成型 tags 速查表</h2><p><strong>不在本表上的数据均为与势力包(bp)和生成均无关的 tags。</strong></p><table><thead><tr><th>英文</th><th>实际意义</th></tr></thead><tbody><tr><td>base_bp</td><td>玩家初始势力包，且绝大多数势力都拥有</td></tr><tr><td>lowtech_bp</td><td>低科势力包，<strong>不是所有低科武器/舰船都在其中</strong>。原版拥有它的势力是：霸主、自由联盟(与拾荒者)、英仙座联盟、辛达强权、卢德教会、卢德左径(仅武器)</td></tr><tr><td>midline_bp</td><td>中科势力包，<strong>不是所有中科武器/舰船都在其中</strong>。原版拥有它的势力是：霸主、自由联盟(与拾荒者)、英仙座联盟、辛达强权、卢德教会(仅武器)、卢德左径(仅武器)</td></tr><tr><td>hightech_bp</td><td>高科势力包，<strong>不是所有高科武器/舰船都在其中</strong>。原版拥有它的势力是：自由联盟(与拾荒者)、英仙座联盟、辛达强权、速子科技、卢德教会(仅武器)、卢德左径(仅武器)</td></tr><tr><td>heg_aux_bp</td><td>霸主辅助军(A)型改造包，只有霸主拥有它</td></tr><tr><td>XIV_bp</td><td>第十四军团(XIV)蓝图包，只有霸主拥有它</td></tr><tr><td>hegemony</td><td>霸主势力包</td></tr><tr><td>independent</td><td>自由联盟势力包</td></tr><tr><td>persean</td><td>英仙座联盟势力包</td></tr><tr><td>tritachyon</td><td>速子科技势力包</td></tr><tr><td>scavenger</td><td>拾荒者(在野外刷出的自由联盟舰船使用此类型)势力包</td></tr><tr><td>luddic_church</td><td>卢德教会势力包</td></tr><tr><td>luddic_path</td><td>卢德左径势力包</td></tr><tr><td>LP_bp</td><td>左径安超(LP)型改造包，只有卢德左径拥有它</td></tr><tr><td>lions_guard</td><td>狮心守卫(辛达强权的少量特殊舰队使用此类型)势力包</td></tr><tr><td>sindrian_diktat</td><td>辛达强权势力包</td></tr><tr><td>pirates</td><td>海盗势力包</td></tr><tr><td>derelict</td><td>遗弃船(绿粽子)势力包</td></tr><tr><td>remnant</td><td>余晖势力包</td></tr><tr><td>omega</td><td>欧米伽势力包</td></tr><tr><td>merc</td><td>精英佣兵包，可以被游戏中自由联盟生成的<em>万国牌多 S 插超豪华佣兵</em>使用。</td></tr><tr><td>rare_bp</td><td>稀有包，所有势力都默认不拥有它。它不是用来控制势力生成，而是让拥有这个 tag 的组件可在野外被打捞或挖坟得出</td></tr><tr><td>restricted</td><td>限制级，所有势力都默认不拥有它。它将默认不出现在数据库与绝大多数场合，适合用于隐藏数据</td></tr><tr><td>no_drop</td><td>不会在战后掉落</td></tr><tr><td>no_drop_salvage</td><td>不会在残骸区打捞得到</td></tr><tr><td>no_sell</td><td>不会在市场出售</td></tr><tr><td>no_dealer</td><td>不会被联络人任务提供</td></tr><tr><td>hist1t</td><td>与历史学家相关，带有此 tag 的组件会被历史学家提供蓝图位置。在历史学家池里的稀有度较低</td></tr><tr><td>hist2t</td><td>与历史学家相关，带有此 tag 的组件会被历史学家提供蓝图位置。在历史学家池里的稀有度较高</td></tr><tr><td>hist3t</td><td>与历史学家相关，带有此 tag 的组件会被历史学家提供蓝图位置。在历史学家池里的稀有度最高</td></tr><tr><td>没有hist4t或更高</td><td></td></tr></tbody></table>',19),i=[e];function u(c,l){return s(),a("div",null,i)}const d=n(o,[["render",u],["__file","index.html.vue"]]);export{d as default};
