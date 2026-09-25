# TODO: Build the new ClickGUI (handoff for a fresh Claude session)

> **If you are a new Claude session: read this whole file, then start coding at "Implementation plan", step 0.**
> The design below was discussed with the user and **approved** on 2026-09-24. The user explicitly asked that the next
> session implement directly from this file. Do **not** re-run brainstorming or ask the design questions again.
> Only ask the user if something here turns out to be impossible, or if a decision is genuinely missing.
> Tick off the checkboxes in this file as you finish steps (and commit), so later sessions can continue too.

---

## 0. THE MOST IMPORTANT RULE

**The GUI must NOT use Minecraft's GUI rendering system at all.** The user said this explicitly. That's why the GUI
size is independent of MC's GUI-scale option: it doesn't live in MC's GUI coordinate space at all.

Not allowed for drawing: `GuiGraphicsExtractor` (old name `GuiGraphics`), `GuiRenderState`, `GuiElementRenderState`,
`GuiRenderer`, MC's `Font` / `font/*.json` / TTF font provider, `Screen` background/blur rendering, vanilla widgets,
guiScaledWidth/Height.

Allowed: Mojang's **low-level** graphics layer `com.mojang.blaze3d.*` (GpuDevice, CommandEncoder, RenderPass, GpuBuffer,
GpuTexture, RenderPipeline), because that's the only way to draw anything and it works on **both** 26.2 backends
(OpenGL **and** Vulkan; both exist in `com/mojang/blaze3d/opengl` and `com/mojang/blaze3d/vulkan`).
**Never call raw OpenGL (GL11 etc.) or NanoVG**: that would break on the Vulkan backend.

Allowed for input only: a minimal `Screen` subclass that **draws nothing**. It only exists so MC frees the cursor and
stops treating clicks and keys as game input. Its event coordinates are ignored; we read raw window pixels ourselves.

---

## 1. What the user wants (their words, condensed)

"Create a clean, small and customizable ClickGUI for the mod. Settings like color pickers, boolean settings, slider
settings, mode settings and everything else needed to adjust the modules' settings. Use the reference image (see §2).
Clean frame-based animations, clean glow and slim categories. Every setting widget should have animations too
(e.g. toggling on/off). The GUI size must be **independent of Minecraft's GUI scale**, and the GUI must not be made
with Minecraft's GUI rendering logic at all. Keep in mind the GUI should be **small**!"

### Decisions made with the user
| Topic | Decision |
|---|---|
| Renderer | **Our own renderer** on blaze3d (`GpuDevice`/`RenderPass`), drawing in **physical window pixels**. Own SDF shader for rounded rects + real glow. |
| Font | **Our own text rendering**: bundled Inter TTF (OFL) rasterized with **FreeType** (`org.lwjgl.util.freetype`, already shipped with MC) into our own glyph-atlas texture. NOT MC's font system. |
| Input | Blank `Screen` subclass, used only to capture input; raw mouse position from `MouseHandler.xpos()/ypos()` |
| List pickers | **Expand inline** inside the panel, with a search field and a small scrollable list (~6 rows) |
| Persistence | **Yes, save everything**: module on/off, keybinds, all setting values, panel positions and collapsed state, ClickGUI theme |
| Look | Like the reference: one slim column per category, dark rows, compact. Small rounded corners + soft accent glow |

---

## 2. Reference image (description, because the image is not in the repo)

Screenshot of a classic "column" ClickGUI over a blurred game background:
- 6 vertical columns (**Combat, Misc, Render, Movement, Player, Client**), each about 145 px wide at 2000 px screen width
  (≈ 7 % of screen width). The header has an icon plus the category name, slightly taller than the rows.
- Module rows are ≈ 22 px tall at that resolution: dark gray/near-black background with light text on the left.
  **Enabled** modules have a lighter / light-gray highlighted row. A bound key appears right-aligned in the row (e.g. `ClickGui  RShift`).
- Right-clicking a module expands its settings **inline under the row** (slightly inset, smaller font):
  - boolean: small checkbox on the right
  - mode: `Mode  UUIDCheck ⌄` (value right-aligned with a chevron)
  - slider: label left, value right, thin track underneath with a small handle
  - color: label left, color swatch right; expanded shows a saturation/value square, a vertical hue bar,
    a vertical alpha bar and `Copy` / `Paste` buttons
  - keybind row: `Cancel   LEFT_SHIFT`
- Tooltip (module description) in a small dark box next to the cursor.
- Search field (`Search...`) at the top of one column (Ctrl+F).
- Help text in the bottom-left corner (left click = toggle, right click = settings, middle click = bind, …).
- The whole thing is compact and crisp. **Ours should be even smaller.**

---

## 3. Project facts (verified)

- **Minecraft 26.2**, Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, Java 25, Mojang mappings (26.2 names; many were
  renamed compared with 1.21!).
- Root package `com.OsamaClient.newbridge` (`src/main/java/com/OsamaClient/newbridge/`). Mod id / namespace `newbridge`.
  Mixins config: `src/main/resources/newbridge.mixins.json` (the project already uses many mixins).
- The **old GUI was deleted** in commit `d21775c`. Right now there is **no way to open a GUI and no module keybinds**.
  The old code is only useful for ideas (`git show e731079:src/main/java/com/OsamaClient/newbridge/UI/ClickGuiScreen.java`,
  `Config.java`). It used MC's GUI rendering, so **don't reuse its drawing code**.
- `EntryPoint.java`: module tick loop, HUD elements, and render pipeline registration
  (`RenderPipelines.register(RenderPipeline.builder(...).withLocation(...).build())`, see the existing no-depth pipelines).
- `UI/components/Module.java`: `name`, `enabled`, `description`, `category`, `List<Component> settings`, `toggle()`
  (calls `onEnable`/`onDisable`). `Category` enum: `COMBAT, MOVEMENT, VISUAL, MISC, Donut`. It has an unused field `keyAlreadyPressed`.
- `UI/components/ModuleManager.java`: static `modules`, `init()`, `getModulesByCategory`, `getModuleByName`.
- **Setting data holders** (in `UI/components/`, all `extends Component`, all have `getLabel()` and `getDescription()`).
  **Keep their public API unchanged**; ~40 module files use them:

  | Class | Uses | Value API |
  |---|---|---|
  | `ToggleButton` | 57 | `public boolean enabled`, `setValue(boolean)` (fires callback) |
  | `Slider` | 65 | `getValue()`, `setValue(double)` (snaps to `step`, clamps, fires callback). **min/max/step/default are private: add getters** |
  | `ModeButton` | 11 | `getIndex()`, `setIndex(int)`. **`modes` is private: add `getModes()`** |
  | `ColorPicker` | 1 | `getColor()`, `setColor(int)` ARGB |
  | `TextBox` | 1 | `getText()`, `setText(String)`, `maxLength`, `numericOnly` (add getters if needed) |
  | `BlockPicker` | 2 | `public Set<Block> selectedBlocks` |
  | `ItemPicker` | 3 | `public Map<Item,Integer> selectedItems` (check the usages in `Hacks/` to see what the Integer means) |
  | `EnchantmentPicker` | 1 | `public Map<String,Integer> selectedEnchantments` (id → level, check its usage) |
  | `EntityFilterPicker` | 7 | `public Map<String,Boolean> filters`, `public Map<String,Integer> colors` (ARGB per key) |

  The `Component` base class still has leftover `x/y/width/height/syncScaledSize`. Leave them; new widgets must not rely on them.
- There are **no tests yet** (`src/test` doesn't exist, no JUnit in `build.gradle`).

### 3.1 Low-level 26.2 graphics API (looked up with `javap` on `.gradle/loom-cache/minecraftMaven/.../minecraft-merged-043a8b3edf-26.2.jar`)
To look up more: `javap -cp <that jar> <class>` (add `-c -p` for bytecode). The best reference for how to draw with
blaze3d is how vanilla does it: **`net.minecraft.client.gui.render.GuiRenderer`**, method `executeDrawRange` and the method
that calls `setVertexBuffer`/`drawIndexed`. Read its bytecode to copy the *pattern*; don't use the class itself.

- `RenderSystem.getDevice()` → `GpuDevice`: `createCommandEncoder()`, `createBuffer(Supplier<String>, int usage, long size | ByteBuffer)`,
  `createTexture(String, int usage, GpuFormat, int w, int h, int depthOrLayers, int mips)`, `createTextureView(GpuTexture)`,
  `createSampler(AddressMode, AddressMode, FilterMode, FilterMode, int maxAniso, OptionalDouble)`.
  Usage flags are int constants on `GpuBuffer` / `GpuTexture` (check with javap).
- `CommandEncoder`: `createRenderPass(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clear, GpuTextureView depth|null, OptionalDouble)`,
  `writeToBuffer(GpuBufferSlice, ByteBuffer)`, `writeToTexture(GpuTexture, ByteBuffer, ...)` / `writeToTexture(GpuTexture, NativeImage)`,
  `copyTextureToTexture(...)` (useful for blur later).
- `RenderPass` (AutoCloseable, use try-with-resources): `setPipeline`, `bindTexture(String samplerName, GpuTextureView, GpuSampler)`,
  `setUniform(String blockName, GpuBufferSlice)`, `enableScissor(x,y,w,h)` / `disableScissor()` (framebuffer pixels),
  `setVertexBuffer(0, slice)`, `setIndexBuffer(GpuBuffer, IndexType)`, `drawIndexed(indexCount, instanceCount=1, firstIndex, baseVertex, firstInstance=0)`.
- Vanilla GUI draw pattern (from `GuiRenderer.executeDrawRange`):
  ```java
  try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
          () -> "newbridge ui", target.getColorTextureView(), Optional.empty(),
          target.useDepth ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
      RenderSystem.bindDefaultUniforms(pass);          // Globals, Projection, etc.
      pass.setUniform("Projection", ourProjectionSlice);  // override with OUR pixel ortho
      pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f()));
      pass.setPipeline(pipeline);
      pass.setVertexBuffer(0, vertexSlice);
      RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
      pass.setIndexBuffer(quads.getBuffer(indexCount), quads.type());
      pass.drawIndexed(indexCount, 1, 0, 0, 0);
  }
  ```
  (Check `DynamicUniforms.writeTransform` overloads with javap; vanilla passes the model-view matrix, and newer versions may
  also want color modulator / offset args.)
- Main framebuffer: `Minecraft.getInstance().getMainRenderTarget()` (or `gameRenderer.mainRenderTarget()`, check which exists) → `RenderTarget`
  (`getColorTextureView()`, `getDepthTextureView()`, `useDepth`, `width`, `height`).
- Projection: `new ProjectionMatrixBuffer("newbridge ui")` + `Projection p = new Projection(); p.setupOrtho(near, far, width, height, invertY);`
  (vanilla GuiRenderer passes `true` for the boolean) → `projBuf.getBuffer(p)` gives the `GpuBufferSlice`. Or use `getBuffer(Matrix4f)` with
  `new Matrix4f().setOrtho(0, fbW, fbH, 0, -1000, 1000)`. Width/height = **framebuffer pixel size** (`window.getWidth()/getHeight()`).
- Vertex buffers: vanilla uses `net.minecraft.client.renderer.StagedVertexBuffer` (`appendDraw(format, topology)`,
  `getVertexBuilder(draw)` → `VertexConsumer`, `upload()`, `getExecuteInfo(draw)`, `endFrame()`). You can use it (it's a low-level
  buffer helper, not GUI logic) or allocate our own `GpuBuffer` and fill it via `ByteBufferBuilder`/`BufferBuilder`. Either is fine.
- `VertexConsumer`: `addVertex(x,y,z)`, `setColor(int argb)`, `setUv(u,v)`, `setUv1(int,int)`, `setUv2(int,int)`, `setNormal(...)`.
- Custom vertex format: `VertexFormat.builder(..).addAttribute("Name", GpuFormat.RGBA32_FLOAT)...build()` (check the builder int arg
  and the attribute-name ↔ shader `in` name mapping with javap). `DefaultVertexFormat.POSITION_TEX_COLOR` is enough for text.
- Pipelines: `RenderPipeline.builder(snippets...)`, `.withLocation(Identifier)`, `.withVertexShader(Identifier)`, `.withFragmentShader(Identifier)`,
  `.withVertexBinding(0, format)`, `.withPrimitiveTopology(PrimitiveTopology.QUADS)`,
  `.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))` (check the constructor),
  `.withDepthStencilState(Optional.empty())`, `.withBindGroupLayout(BindGroupLayouts.SAMPLER0)` for the text pipeline, `.withCull(false)`, `.build()`.
  `RenderPipelines.GUI_SNIPPET` / `GUI_TEXTURED_SNIPPET` are useful *snippets* for uniform layout (Globals/Projection/DynamicTransforms);
  reusing a snippet is fine, it's pipeline config, not GUI logic. Register once via `RenderPipelines.register(...)` in `EntryPoint`.
- Shaders live in `src/main/resources/assets/newbridge/shaders/core/*.vsh|.fsh`, referenced as `newbridge:core/ui_sdf`.
  Uniform header to copy (from vanilla `core/gui.vsh`, `#version 330`):
  `layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };`
  `layout(std140) uniform Projection { mat4 ProjMat; };`
  Vanilla compiles GLSL to SPIR-V for Vulkan itself (`blaze3d/vulkan/glsl`), so plain GLSL 330 is right for both backends.
  If our custom namespace shaders aren't found, check how `ShaderManager` discovers shader resources (there's a `ShaderManagerMixin` already in the project).
- Hook point for drawing: **mixin into `GameRenderer.render(DeltaTracker, boolean)`**, injecting **after** the call to
  `GuiRenderer.render()` (and before `GuiRenderer.endFrame()`). That way our GUI draws on top of everything
  (world, HUD, chat) every frame. Draw only when `minecraft.screen instanceof ClickGuiScreen` (or while the close animation is still running).
- Window/mouse: `Window`: `getWidth()/getHeight()` = framebuffer px, `getScreenWidth()/getScreenHeight()` = window coords,
  `handle()`. `MouseHandler.xpos()/ypos()` are in window coords → framebuffer px = `xpos * getWidth() / getScreenWidth()`
  (HiDPI/macOS safe).
- Screen input API (only for forwarding): `mouseClicked(MouseButtonEvent, boolean)`, `mouseReleased(MouseButtonEvent)`,
  `mouseDragged(MouseButtonEvent, double, double)`, `mouseScrolled(double, double, double hAmount, double vAmount)`,
  `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)` (`net.minecraft.client.input`). Render hooks to override as no-ops:
  `extractRenderState(GuiGraphicsExtractor, int, int, float)` and `extractBackground(GuiGraphicsExtractor, int, int, float)`.
  `isPauseScreen()` → false.
- FreeType: `org.lwjgl.util.freetype.FreeType` (`FT_Init_FreeType`, `FT_New_Memory_Face`, `FT_Set_Pixel_Sizes`, `FT_Load_Char` with
  `FT_LOAD_RENDER`, glyph bitmap/metrics, `FT_Get_Kerning`). MC's own `com.mojang.blaze3d.font.TrueTypeGlyphProvider` uses exactly
  this; read its bytecode for a working example of calling FreeType from Java in 26.2 (memory handling with `MemoryStack`/`MemoryUtil`).

---

## 4. Design

### 4.1 Package layout (new code under `UI/gui/`)
```
UI/gui/
  ClickGuiScreen.java        blank Screen: captures input, forwards it to ClickGui; draws NOTHING itself
  ClickGui.java              the GUI root: panels, input routing, open/close anim, tooltip, search, help line, frame()
  Panel.java                 one category column: header, drag, collapse anim, scroll, list of ModuleRow
  ModuleRow.java             module row: toggle anim, expand anim, bind mode, owns SettingWidgets
  ClickGuiModule.java        "ClickGui" module (CLIENT category), holds the theme settings, default key RShift
  Theme.java                 static accessors reading ClickGuiModule settings (accent, scale, radius, glow, speed…)
  anim/Anim.java             frame-time animation value (exp smoothing + easing), pure Java, unit-tested
  util/ColorUtil.java        HSV<->RGB, hex parse/format, lerpColor, alpha multiply, pure Java, unit-tested
  input/UiInput.java         raw mouse pos in framebuffer px → GUI units, button/scroll/key/char events
  render/UiRenderer.java     frame lifecycle: begin(fbW,fbH) → collect → flush() into one RenderPass; scissor stack; pixel ortho
  render/UiPipelines.java    registers ui_sdf + ui_text pipelines (called from EntryPoint)
  render/ShapeBatch.java     SDF quads (rect / rounded / outline / glow / gradient) → vertex data
  render/TextBatch.java      glyph quads → vertex data
  render/font/FontAtlas.java FreeType rasterizer + atlas GpuTexture (rebuilt when the pixel size changes)
  render/font/UiFont.java    width(), height(), draw(), ellipsize()
  render/Ui.java             draw facade used by widgets: rect/round/outline/glow/gradient/text/textWidth/pushClip/popClip
  setting/SettingWidget.java abstract base: height(), render(Ui), mouse/key handlers, anim state
  setting/SettingWidgets.java factory: Component -> SettingWidget
  setting/BoolWidget, SliderWidget, ModeWidget, ColorWidget, TextWidget, ListPickerWidget (Block/Item/Enchantment),
          EntityFilterWidget, BindWidget
mixin/GameRendererUiMixin.java  hook after GuiRenderer.render() → ClickGui.frame()
config/Config.java           JSON save/load (Gson is on the classpath via Minecraft)
```
Keep files focused. No file over ~400 lines; split if one grows.

### 4.2 Coordinates and independent scaling
- The renderer works in **framebuffer pixels** (ortho `0..fbW × 0..fbH`, y down).
- Widgets work in **GUI units**. `Theme.scale()` = physical pixels per unit (ClickGuiModule slider "Scale",
  1.0–3.0, step 0.25, default **1.5**). `Ui` multiplies every coordinate by the scale before handing it to the batches.
- Mouse: framebuffer px (§3.1) ÷ scale → units. MC's GUI scale is **never read**.
- Round rect edges to whole physical pixels where it matters (row separators, 1px outlines), so everything stays crisp.

### 4.3 Sizes (GUI units; at the default scale 1.5 → ×1.5 physical px)
- Panel width **92**, header height **14**, module row **11**, setting row **10**, inner padding 3, gap between panels 6.
- Font: **7 units** for modules, 6.5 for settings. The font atlas is rasterized at `round(size * scale)` **physical** pixels, so
  glyphs are drawn 1:1 = maximally crisp. When the scale changes, rebuild the atlas (cache per pixel size).
- Panels start in a row at top-left (x = 8 + i·(92+6), y = 8), then keep the user's dragged positions (persisted).

### 4.4 Renderer details
**UiRenderer frame:** `begin()` (read fb size, reset batches) → widgets call `Ui` → `flush()`: upload vertices, one
`RenderPass` on the main render target, draw commands **in submission order**. Shapes and text interleave (row background,
then text, then the next row…), so keep an ordered command list of `(pipeline, texture, scissor, firstVertex, count)` and merge
consecutive commands with identical state. Scissor = `pass.enableScissor` in framebuffer pixels (watch the Y origin:
check whether blaze3d's scissor is bottom-left like GL; `GuiRenderer.enableScissor` bytecode shows the conversion vanilla does).

**SDF pipeline `newbridge:pipeline/ui_sdf`:** custom vertex format, per vertex:
- `Position` (vec3), `Color` (RGBA8, gradients = different colors on the 4 vertices)
- `Local` (vec2) = pixel offset from the rect center
- `Rect` (vec4) = (halfW, halfH, radius, param) in px; `param` = outline thickness or glow radius
- `Mode` (float or int) = 0 fill, 1 outline, 2 glow
Fragment: `q = abs(local) - (half - r); d = length(max(q,0)) + min(max(q.x,q.y),0) - r;`
- fill: `a = clamp(0.5 - d, 0, 1)` (d is in px → exact 1px AA; or `d / fwidth(d)`)
- outline: `a = clamp(0.5 - (abs(d + t*0.5) - t*0.5), 0, 1)`
- glow: the quad is expanded by `glow` px; `a = pow(1 - smoothstep(0, glow, max(d,0)), 2)` (tune for a soft, clean falloff)
- output `vec4(color.rgb, color.a * a)`, `discard` if `a == 0`.
Hue bar = 6 gradient quads; SV square = horizontal white→hue gradient + vertical transparent→black gradient on top.

**Text pipeline `newbridge:pipeline/ui_text`:** `POSITION_TEX_COLOR`, sampler `Sampler0` = the atlas (R8 or RGBA8 with alpha
coverage), `fragColor = vec4(color.rgb, color.a * texture(Sampler0, uv).r)`. Nearest filtering (glyphs are 1:1 pixels);
snap the glyph origin to whole pixels.

**Font atlas:** Inter TTF at `assets/newbridge/fonts/inter.ttf` (load with `Minecraft.getResourceManager()` or
`getClass().getResourceAsStream`, copy into a direct `ByteBuffer` that stays alive while the face is used!). Rasterize
ASCII 32–126 + Latin-1 (äöüß etc.) up front, other glyphs lazily. Use kerning if available. Icons like the chevron and
checkmark are drawn with SDF shapes / small line quads, **not** glyphs.

**Background:** ClickGuiModule `Background` = `Dim` (default: full-screen black quad, alpha 0.35 × open progress), `None`,
or `Blur`. Blur is **optional, last step**: copy the main target into our own texture and run 2–3 dual-Kawase down/up passes
with our own pipelines. Skip it if it gets hairy, and tell the user.

### 4.5 Animations (`Anim`)
- Frame-based: every `ClickGui.frame()` compute `dt` from `System.nanoTime()` (clamp to 0.1 s).
  `value += (target - value) * (1 - exp(-speed * dt))`, `speed = base * Theme.animSpeed()`.
  Plus an eased 0→1 `progress` helper (easeOutCubic / easeInOutQuad) for open/close.
- Pure Java (no MC classes), so it can be unit-tested.
- What animates:
  - GUI open: panels fade in and slide down 6 units, staggered ~40 ms per panel. Close does the reverse, and **only then**
    closes the Screen (keep drawing during the close animation).
  - Module toggle: accent fill fades in, accent bar on the left grows (0→2 units), text color lerps, glow fades in.
  - Hover: row brightens slightly (all rows and widgets).
  - Expand/collapse of settings and panels: animated height with scissor clipping.
  - Bool: pill switch knob slides and the track color lerps to the accent.
  - Slider: fill width eases to the value; knob grows on hover/drag.
  - Mode: the dropdown list expands (animated height), chevron rotates or flips.
  - Color: picker area expands; the swatch gets a glow on hover.
  - Bind mode: row text pulses (`Press a key…`).

### 4.6 Interaction
- **Module row:** LMB = toggle, RMB = expand/collapse settings, MMB = bind mode (next key binds it;
  ESC/DELETE/BACKSPACE = unbind). Hover shows the description in a small tooltip.
- **Panel header:** LMB-drag = move, RMB = collapse.
- **Mouse wheel** over a panel scrolls it when it's taller than the window.
- **Slider:** drag. Shift+LMB = type a value; Enter commits. Arrow keys while hovered = ±step (if step is 0, ±1 % of the range).
- **Color:** click the swatch → expand. Drag in the SV square, hue bar, alpha bar. Click the hex field to edit it.
  Copy/Paste via the clipboard (`minecraft.keyboardHandler.getClipboard()/setClipboard()`; check the names).
- **Pickers:** click the row (`Blocks  3 selected`) → expand with a search box + ~6-row scroll list of registry entries
  (`BuiltInRegistries.BLOCK / ITEM`, enchantments from the level's registry access). Click toggles; selected entries sort
  first and get the accent color.
- **EntityFilter:** expands to one bool row per key, each with a small color swatch (same inline color picker).
- **Ctrl+F:** search field at the top center; filters modules across all panels by name (case-insensitive).
- **ESC** closes (with the animation). The GUI doesn't pause the game.
- Help line bottom-left, dim.

### 4.7 ClickGuiModule / customization
- Add `CLIENT` to `Module.Category`; register `ClickGuiModule` in `ModuleManager.init()`.
- Toggling it opens the screen and immediately resets `enabled` to false. Default key: **Right Shift**.
- Settings (existing data holders → edited and persisted like everything else):
  - `Accent` ColorPicker `0xFF6C8CFF` · `Scale` Slider 1.0–3.0 step 0.25 = 1.5 · `Radius` Slider 0–5 step 0.5 = 2.5 ·
    `Glow` Slider 0–1 step 0.05 = 0.6 · `Anim Speed` Slider 0.25–3 step 0.25 = 1 · `Background` Mode `Dim/None/Blur` ·
    `Outline` Toggle = true · `Descriptions` Toggle = true
- Base palette (fixed): panel background `0xF0121216`, row `0xFF18181D`, row hover `0xFF1F1F26`, text `0xFFE6E6EB`,
  dim text `0xFF8A8A96`, setting background `0xFF141418`.

### 4.8 Keybinds
- `Module`: add `public int key = -1;` (GLFW key code).
- In the client tick: if `client.screen == null`, detect a **fresh press** per bound module (keep the previous state in a map;
  read the key state with `InputConstants.isKeyDown(window, key)`, check its signature with javap) → `toggle()`.

### 4.9 Persistence (`config/Config.java`)
- `<gameDir>/config/0smasclient.json` (`FabricLoader.getInstance().getConfigDir()`), pretty Gson:
  ```json
  { "version": 1,
    "modules": { "KillAura": { "enabled": false, "key": -1,
                  "settings": { "Range": 4.5, "Mode": "Single", "Players": {"filters":{...},"colors":{...}} } } },
    "panels":  { "COMBAT": { "x": 8, "y": 8, "collapsed": false } } }
  ```
- Keyed by `getLabel()` (duplicate labels get the suffix `#2`, `#3`). bool → boolean, slider → number, mode → mode **name**
  (fall back to index 0), color → `#AARRGGBB`, text → string, block/item → registry ids (item `{id: count}`),
  enchant `{id: level}`, entity filter → both maps.
- Load right after `ModuleManager.init()` via the setters (callbacks fire). For enabled modules call `toggle()` inside try/catch
  (no world/player at startup).
- Save on GUI close, on `ClientLifecycleEvents.CLIENT_STOPPING`, and after rebinding. Unknown/missing keys are ignored; a broken
  file is logged, never crashes the game.

---

## 5. Implementation plan (do these in order, commit after each step)

Build: `./gradlew build` (Windows: `.\gradlew.bat build`). Run: `./gradlew runClient`. The user tests visually in-game.
After a visual step, tell them exactly what to look at. Commits go on `master` (that's what the repo does). End commit
messages with the attribution lines your harness gives you. Test both backends if the game has an option for it
(Video Settings → graphics API / renderer, or a launch flag; find out with javap/strings on `blaze3d/vulkan`).

- [x] **0. Renderer spike.** `UiPipelines`, `ui_sdf` shaders, `UiRenderer` + `ShapeBatch`, the `GameRenderer` mixin hook, and a
      blank `ClickGuiScreen` (temporarily opened by RShift). Draw one rounded rect + glow + gradient at fixed pixel coordinates.
      Verify: correct position/size in **physical pixels**, AA edges, blending over the world, a resize works, changing MC's GUI
      scale changes **nothing**, no GL errors, works on OpenGL (and Vulkan if selectable).
      If it really can't be made to work → **stop and ask the user**.
- [x] **1. Font.** Inter + OFL license file, `FontAtlas` (FreeType) + `TextBatch` + `ui_text` pipeline + `UiFont`. Verify crisp text
      at scale 1, 1.5, 2, 3 and correct widths (right-aligned values line up).
- [x] **2. Pure logic + tests.** Add JUnit 5 to `build.gradle` (`testImplementation 'org.junit.jupiter:junit-jupiter:5.x'`,
      `test { useJUnitPlatform() }`). Write `Anim` + `ColorUtil` test-first. `./gradlew test` passes.
- [ ] **3. Data holder getters.** `Slider` min/max/step/default, `ModeButton.getModes()`, `TextBox` getters, `Module.key`,
      `Category.CLIENT`. Nothing else in modules changes.
- [ ] **4. ClickGui + Panel + ModuleRow.** Scaling, raw input, dragging, collapse, scroll, toggle/hover/expand animations, tooltip,
      help line, open/close animation, Dim background. `ClickGuiModule` + `Theme`.
- [ ] **5. Keybinds.** Tick-based key handling, RShift opens the GUI, `BindWidget`, MMB bind mode.
- [ ] **6. Setting widgets.** Bool → Slider → Mode → Color (full picker) → Text → EntityFilter → ListPicker. Look at each one in-game.
- [ ] **7. Search** (Ctrl+F).
- [ ] **8. Config persistence** + round-trip unit test of the MC-free JSON mapping.
- [ ] **9. Polish.** Compare against §2: compact, crisp, consistent spacing; smooth at 30 and 240 FPS; MC GUI scale changes nothing;
      854×480 and 4K both usable; no per-frame allocations in hot paths (reuse buffers); no GPU resource leaks
      (close textures/buffers on resize/scale change and on shutdown; an earlier ESP bug leaked GPU buffers and crashed after 20 min!).
- [ ] **10. Optional blur background** (§4.4). Skip if it isn't clean.
- [ ] **11. Cleanup.** Remove spike code, final `./gradlew build` + `test`, tick all boxes here, and tell the user what's done
      (and anything that was skipped).

### Definition of done
- RShift opens a small, clean column GUI with all 6 categories (Combat, Movement, Visual, Misc, Donut, Client).
- **Nothing is drawn through Minecraft's GUI system**; our own blaze3d renderer + own font only.
- Every setting type used by the modules is editable in the GUI and animates.
- Soft accent glow + rounded corners via the SDF shader; crisp FreeType text.
- GUI size is independent of MC's GUI scale and adjustable in the ClickGui module.
- Everything persists across restarts; a broken config never crashes the game.
- `./gradlew build` and `./gradlew test` pass.
