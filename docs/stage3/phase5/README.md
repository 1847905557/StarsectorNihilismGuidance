# 制作弹体尾迹

*本节预期阅读时间：~10 分钟。*

*本节预期操作时间：~10 分钟。*

## 熟悉文件结构

弹体尾迹文件在`data/trails`文件夹中的`trails.csv`。

```csv
trail,projectile,sprite,minLength,fadeIn,duration,fadeOut,sizeIn,sizeOut,colorIn,colorOut,opacity,textLength,textScroll,distance,dispersion,drift,fadeOnFadeOut,angleAdjustment,additive,velocityIn,velocityOut,randomVelocity,angle,rotationIn,rotationOut,randomRotation,renderBelowExplosions

```

如同其它一切 CSV 一样，将第一行视作表头，从第二行开始填写。

编者更建议从 trailermoments 等公开范例里抄一份然后进行修改或微调。

## 弹体尾迹速查表

| 英文            | 实际意义                                                     |
| --------------- | ------------------------------------------------------------ |
| trail           | 尾迹的 ID，必须互相独特                                      |
| projectile      | 尾迹所属的弹体的 ID。一个弹体可以同时被多种尾迹使用          |
| sprite          | 尾迹所使用的材质，默认可用的有`base_trail_smooth`、`base_trail_contrail`、`base_trail_mild`等，详见 MagicLib 的`settings.json`，预览图在其`graphic/magic/fx`文件夹内 |
| minLength       | 每隔多少距离分为一段。尾迹是按段渲染的，因此本数字过大会导致渲染错误，不建议高于 10 |
| fadeIn          | 尾迹每一段的渐入时长                                         |
| duration        | 尾迹每一段的满亮度时长                                       |
| fadeOut         | 尾迹每一段的渐出时长                                         |
|                 | **尾迹的整体长度等于 弹体速度*尾迹每一段的总时长**           |
| sizeIn          | 尾迹在最前端的宽度                                           |
| sizeOut         | 尾迹在最尾端的宽度，从`sizeIn`渐变而来                       |
| colorIn         | 尾迹在最前端的颜色                                           |
| colorOut        | 尾迹在最尾端的颜色，从`colorIn`渐变而来                      |
| opacity         | 尾迹透明度，1 为原材质透明度，0 为完全透明                   |
| textLength      | 尾迹材质拉伸程度，不填为不拉伸                               |
| textScroll      | 尾迹材质滚动速度                                             |
| distance        | 尾迹头部与弹体的前后距离偏移                                 |
| dispersion      | ?                                                            |
| drift           | ?                                                            |
| fadeOnFadeOut   | 如果为 true，则当弹体命中或消失时，每一段尾迹都直接开始渐出  |
| angleAdjustment | 弹体角度随尾迹晃动而变化，**极度不建议写 true**              |
| additive        | 如果为 true，则尾迹以叠加模式渲染，**极度建议写 true**       |
| velocityIn      | 尾迹每一段在最前端的偏移速度                                 |
| velocityOut     | 尾迹每一段在最尾端的偏移速度                                 |
| randomVelocity  | 尾迹每一段随机产生的偏移速度                                 |
| angle           | 尾迹产生偏移速度的角度                                       |
| rotationIn      | 尾迹每一段在最前端的旋转速度                                 |
| rotationOut     | 尾迹每一段在最前端的旋转速度                                 |
| randomRotation  | 如果为 true，则旋转变为随机旋转                              |

