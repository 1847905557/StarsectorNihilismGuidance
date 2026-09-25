# 着色器与渲染

本章技术基线：Starsector `0.98a-RC8`；前置依赖：BoxUtil `1.5.5`。

本章以跟随舰船的方向标为连续成果。先用 Java OpenGL 与客户端顶点数组绘制基础图元，再用 GLSL 1.20 的 shader program 完成顶点变换并在片元阶段程序化生成形状，随后将顶点数据迁移到 VBO 并使用 VAO、通过 SSBO 传递实例参数，最后展示 BoxUtil 的接入价值。

## 本章各节

<!-- AUTO:SECTION-LIST -->
