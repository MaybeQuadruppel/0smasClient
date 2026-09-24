# TODO: Build the new ClickGUI (handoff for a fresh Claude session)

> **If you are a new Claude session: read this whole file, then start coding at "Implementation plan", step 0.**
> The design below was discussed with the user and **approved** on 2026-09-24. The user explicitly asked that the next
> session implement directly from this file. Do **not** re-run brainstorming or ask the design questions again.
> Only ask the user if something here turns out to be impossible, or if a decision is genuinely missing.
> Tick off the checkboxes in this file as you finish steps (and commit), so later sessions can continue too.

---

## 1. What the user wants (their words, condensed)

"Create a clean, small and customizable ClickGUI for the mod. Settings like color pickers, boolean settings, slider
settings, mode settings and everything else needed to adjust the modules' settings. Use the reference image (see §2).
Clean frame-based animations, clean glow and slim categories. Every setting widget should have animations too
(e.g. toggling on/off). The GUI size must be **independent of Minecraft's GUI scale**. Keep in mind the GUI should be **small**!"

### Decisions made with the user
| Topic | Decision |
|---|---|
| Rendering | **Custom SDF shader pipeline** (rounded rects + real glow) in the 26.2 GUI renderer. The user chose this over plain `fill()`. |
| Font | **Bundled TTF** (Inter, OFL license) through Minecraft's built-in `ttf` font provider with high oversample |
| List pickers | **Expand inline** inside the panel, with a search field and a small scrollable list (~6 rows) |
| Persistence | **Yes, save everything**: module on/off, keybinds, all setting values, panel positions and collapsed state, ClickGUI theme |
| Look | Like the reference: one slim column per category, dark rows, compact. Small rounded corners + soft accent glow (our addition) |

---

## 2. Reference image (description, because the image is not in the repo)

Screenshot of a classic "column" ClickGUI over a blurred game background:
- 6 vertical columns (**Combat, Misc, Render, Movement, Player, Client**), each about 145 px wide at 2000 px screen width
  (≈ 7 % of screen width). The header has an icon plus the category name, slightly taller than the rows.
- Module rows are ≈ 22 px tall at that resolution: dark gray/near-black background with light text on the left.
  **Enabled** modules have a lighter / light-gray highlighted row. A bound key appears right-aligned in the row (e.g. `ClickGui  RShift`).
- Right-clicking a module expands its settings **inline under the row** (slightly inset, smaller font):
  - boolean: small checkbox on the right (`v`/`x` style)
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

- **Minecraft 26.2**, Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, Java 25, Mojang mappings (names below are
  the 26.2 Mojmap names; many were renamed compared with 1.21!).
- Root package: `com.OsamaClient.newbridge` (`src/main/java/com/OsamaClient/newbridge/`). Mod id / namespace: `newbridge`.
  Mixins config: `src/main/resources/newbridge.mixins.json`.
- The **old GUI was deleted** in commit `d21775c` ("Remove the entire GUI, settings UI, HUD-config and persistence").
  Right now there is **no way to open a GUI and no module keybinds**. You may look at the old code for ideas with
  `git show e731079:src/main/java/com/OsamaClient/newbridge/UI/ClickGuiScreen.java` (and `Config.java`,
  `UI/components/ColorPicker.java` at `c39662a`/`2cc996b`). Do **not** restore it; it was a 1,774-line monolith.
- `EntryPoint.java` registers the module tick loop, HUD elements (`HudElementRegistry.addLast`), and render pipelines
  (`RenderPipelines.register(RenderPipeline.builder(...).withLocation(...).build())`, see the existing no-depth pipelines).
- `UI/components/Module.java`: `name`, `enabled`, `description`, `category`, `List<Component> settings`, `toggle()`
  (calls `onEnable`/`onDisable`). `Category` enum: `COMBAT, MOVEMENT, VISUAL, MISC, Donut`.
  It has an unused field `keyAlreadyPressed`.
- `UI/components/ModuleManager.java`: static `modules` list, `init()`, `getModulesByCategory`, `getModuleByName`.
- **Setting data holders** (in `UI/components/`, all `extends Component`, all have `getLabel()` and `getDescription()`).
  **Keep their public API unchanged**; ~40 module files use them:

  | Class | Uses | Value API |
  |---|---|---|
  | `ToggleButton` | 57 | `public boolean enabled`, `setValue(boolean)` (fires callback) |
  | `Slider` | 65 | `getValue()`, `setValue(double)` (snaps to `step`, clamps, fires callback). **min/max/step/default are private: add getters** |
  | `ModeButton` | 11 | `getIndex()`, `setIndex(int)`. **`modes` list is private: add `getModes()`** |
  | `ColorPicker` | 1 | `getColor()`, `setColor(int)` ARGB |
  | `TextBox` | 1 | `getText()`, `setText(String)`, `maxLength`, `numericOnly` (add getters if needed) |
  | `BlockPicker` | 2 | `public Set<Block> selectedBlocks` |
  | `ItemPicker` | 3 | `public Map<Item,Integer> selectedItems` (check the usages in `Hacks/` to see what the Integer means before building its UI) |
  | `EnchantmentPicker` | 1 | `public Map<String,Integer> selectedEnchantments` (id → level, check its usage) |
  | `EntityFilterPicker` | 7 | `public Map<String,Boolean> filters`, `public Map<String,Integer> colors` (ARGB per key) |

  The `Component` base class still has leftover `x/y/width/height/syncScaledSize`. Leave them; the new widgets must not rely on them.
- There are **no tests yet** (`src/test` doesn't exist, no JUnit in `build.gradle`).

### 26.2 API facts (looked up with `javap` on `.gradle/loom-cache/minecraftMaven/.../minecraft-merged-043a8b3edf-26.2.jar`)
- `GuiGraphics` is now **`net.minecraft.client.gui.GuiGraphicsExtractor`**. Useful members: `pose()` →
  `org.joml.Matrix3x2fStack` (2D! `pushMatrix/scale/translate/popMatrix`), `fill(...)`, `fillGradient(...)`,
  `text(Font, String|Component|FormattedCharSequence, x, y, color[, shadow])`, `enableScissor(x0,y0,x1,y1)`/`disableScissor()`,
  `blit(...)`, and the public field `guiRenderState` (`net.minecraft.client.renderer.state.gui.GuiRenderState`) with
  `addGuiElement(GuiElementRenderState)`.
- `Screen` (`net.minecraft.client.gui.screens.Screen`): draw in
  `extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick)`; background via
  `extractBackground(...)` / `extractBlurredBackground(g)` / `extractTransparentBackground(g)`. Also `init()`, `onClose()`,
  `removed()`, `isPauseScreen()`.
- Input (from `GuiEventListener`): `mouseClicked(MouseButtonEvent, boolean doubleClick)`, `mouseReleased(MouseButtonEvent)`,
  `mouseDragged(MouseButtonEvent, double dx, double dy)`, `mouseScrolled(double x, double y, double h, double v)`,
  `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)` (in `net.minecraft.client.input`). Check the record accessors with `javap`.
- `GuiElementRenderState` interface: `buildVertices(VertexConsumer)`, `pipeline()`, `textureSetup()`, `scissorArea()`,
  plus `bounds()` from `ScreenArea`. Model it on `ColoredRectangleRenderState` (a record taking
  `RenderPipeline, TextureSetup, Matrix3x2fc pose, x0,y0,x1,y1, col1,col2, ScreenRectangle scissor[, bounds]`).
  Use `TextureSetup.noTexture()`. Vertices go through `vc.addVertexWith2DPose(pose, x, y).setColor(..).setUv(..).setUv1(..).setUv2(..).setNormal(..)`.
- `GuiRenderer` reads each element's `pipeline.getVertexFormatBinding(...)`, so **custom vertex formats work**.
  `RenderPipelines.GUI_SNIPPET` = core `gui` shaders + `POSITION_COLOR` + QUADS + TRANSLUCENT blend.
  `RenderPipeline.Builder` has `withVertexShader/withFragmentShader(Identifier)`, `withVertexBinding(int, VertexFormat)`,
  `withPrimitiveTopology`, `withColorTargetState`, `withDepthStencilState(Optional.empty())`, `withBindGroupLayout`, `withCull`.
- Vanilla GUI shaders (`assets/minecraft/shaders/core/gui.vsh/.fsh`, `#version 330`) use uniform blocks
  `DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; }` and
  `Projection { mat4 ProjMat; }`. Copy that header into our shaders.
- `DefaultVertexFormat.ENTITY` has inputs `vec3 Position; vec4 Color; vec2 UV0; ivec2 UV1; ivec2 UV2; vec3 Normal;`.
  Alternatively build our own with `VertexFormat.builder(..).addAttribute(name, GpuFormat)`.
- Fonts: `Style.withFont(FontDescription)`, `new FontDescription.Resource(Identifier)`. The TTF provider exists
  (`TrueTypeGlyphProviderDefinition`: `file`, `size`, `oversample`, `shift`, `skip`).
- Keys: GLFW constants (`org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT`). Check key state with
  `InputConstants.isKeyDown(window, key)`; check its 26.2 signature with `javap` (`com.mojang.blaze3d.platform.InputConstants`).

---

## 4. Design

### 4.1 Package layout (new code under `UI/gui/`)
```
UI/gui/
  ClickGuiScreen.java        Screen: panels, input routing, open/close anim, tooltip, search, help line
  Panel.java                 one category column: header, drag, collapse anim, scroll, list of ModuleRow
  ModuleRow.java             module row: toggle anim, expand anim, bind mode, owns SettingWidgets
  ClickGuiModule.java        the "ClickGui" module (CLIENT category), holds theme settings, default key RShift
  Theme.java                 static accessors reading ClickGuiModule settings (accent, radius, glow, speed…)
  anim/Anim.java             frame-time animation value (exp smoothing + easing), pure logic (unit-tested)
  util/ColorUtil.java        HSV<->RGB, hex parse/format, lerpColor, alpha mul (unit-tested)
  render/UiPipelines.java    registers the SDF RenderPipeline (called from EntryPoint)
  render/SdfRectState.java   record implements GuiElementRenderState (writes the 4 vertices)
  render/Ui.java             draw facade: rect/round/outline/glow/gradient/text/textWidth/scissor
  setting/SettingWidget.java abstract base: height(), render(), mouse/key handlers, anim state
  setting/SettingWidgets.java factory: Component -> SettingWidget
  setting/BoolWidget.java, SliderWidget.java, ModeWidget.java, ColorWidget.java, TextWidget.java,
  setting/ListPickerWidget.java (Block/Item/Enchantment), EntityFilterWidget.java, BindWidget.java
config/Config.java           JSON save/load (Gson is on the classpath via Minecraft)
```
Keep files focused. No file over ~400 lines; split if one grows.

### 4.2 Independent scaling
- `Theme.scale()` = **physical pixels per GUI unit** (ClickGuiModule slider "Scale", 1.0–3.0, step 0.25, default **1.5**).
- In `extractRenderState`: `float f = Theme.scale() / (float) minecraft.getWindow().getGuiScale();`
  then `g.pose().pushMatrix(); g.pose().scale(f, f);` and draw everything in **GUI units**. Pop at the end.
- Mouse: `ux = mouseX / f`, `uy = mouseY / f` in every input handler. Virtual screen size = `width / f`, `height / f`.
- The result: changing Minecraft's GUI scale does **not** change our GUI's size.
- Scissor: `enableScissor` takes coordinates in the *current pose space*, so check whether 26.2 transforms them by the pose
  (look at `GuiGraphicsExtractor$ScissorStack`). If it doesn't, multiply by `f` yourself.

### 4.3 Sizes (GUI units; at the default scale 1.5 → ×1.5 physical px)
- Panel width **92**, header height **14**, module row **11**, setting row **10**, inner padding 3, gap between panels 6.
- Font: TTF `size` ≈ 7 with oversample ≈ 4–6 (tune until text looks crisp at scale 1–3). Setting text is slightly dimmer than module text.
- Panels start in a row at top-left (x = 8 + i·(92+6), y = 8), then keep the user's dragged positions (persisted).

### 4.4 SDF render pipeline (the core of "clean glow")
- Pipeline `newbridge:pipeline/ui_sdf`: based on `RenderPipelines.GUI_SNIPPET`, but with our shaders
  `assets/newbridge/shaders/core/ui_sdf.vsh/.fsh` and a vertex binding that carries per-quad parameters.
  Recommended: `DefaultVertexFormat.ENTITY` (or a custom format) with
  - `UV0` (vec2) = local position relative to the rect center, in GUI units
  - `UV1` (ivec2) = half size ×16 (fixed point)
  - `UV2` (ivec2) = (radius ×16, glow/softness ×16)
  - `Normal` or a spare field = mode (fill / outline thickness / glow). If `Normal` is packed as signed bytes, precision is
    low, so use it only for small enums. Otherwise define a custom format.
  - `Color` = color (for gradients, give the 4 vertices different colors; linear interpolation does the rest)
- Fragment: rounded-box SDF `d = length(max(abs(p) - (half - r), 0)) + min(max(q.x,q.y),0) - r`.
  - fill: `alpha = clamp(0.5 - d / fwidth(d), 0, 1)` (anti-aliased at any scale)
  - outline: `abs(d + t/2) - t/2` with the same AA
  - glow: quad expanded by `glow` units; `alpha = (1 - smoothstep(0, glow, d))^2` (or exp falloff) × color alpha
- `SdfRectState` expands the quad for glow, applies the current `g.pose()` (copy it: `new Matrix3x2f(g.pose())`) and the
  current scissor, and computes `bounds()` for culling.
- **Step 0 is a spike**: prove that one rounded rect + one glow renders correctly (right place, AA, blending, scaling) before
  building anything else. **If it can't be made to work, STOP and tell the user.** The fallback would be the old
  "approach A" (plain `fill()` + a baked 9-slice glow texture), but only with the user's OK.
- The color picker's hue bar needs 6 segments (7 stops) → draw 6 gradient quads. The SV square = white→hue horizontal gradient
  plus a transparent→black vertical overlay.

### 4.5 Font
- Download **Inter** (OFL-1.1) regular/medium TTF and put it at `src/main/resources/assets/newbridge/font/inter.ttf`
  (add the OFL license text next to it as `inter_LICENSE.txt`).
  If downloading is impossible, ask the user to drop a TTF there.
- `assets/newbridge/font/ui.json`:
  `{"providers":[{"type":"ttf","file":"newbridge:inter.ttf","size":7,"oversample":5,"shift":[0,0.5],"skip":""}]}`
  (verify the exact codec field names/format against `TrueTypeGlyphProviderDefinition.CODEC` if it fails to load).
- `Ui.text(...)` wraps strings in `Component.literal(s).withStyle(st -> st.withFont(new FontDescription.Resource(id("ui"))))`;
  `Ui.textWidth` uses `font.width(component)`. Cache the style object.

### 4.6 Animations (`Anim`)
- Frame-based: every frame compute `dt` from `System.nanoTime()` (clamp to 0.1 s).
  `value += (target - value) * (1 - exp(-speed * dt))`, with `speed = base * Theme.animSpeed()`.
  Plus an eased 0→1 `progress` helper (easeOutCubic / easeInOutQuad) for open/close.
- `Anim` is pure Java (no MC classes) so it can be unit-tested.
- What animates:
  - GUI open: panels fade in and slide down 6 units, staggered ~40 ms per panel. Close does the reverse, then `onClose`.
  - Module toggle: accent fill fades in, accent bar on the left grows (0→2 units), text color lerps, glow fades in.
  - Hover: row brightens slightly (all rows and widgets).
  - Expand/collapse of settings and panels: animated height with scissor clipping.
  - Bool: pill switch knob slides and the track color lerps to the accent.
  - Slider: fill width eases to the value; knob grows on hover/drag.
  - Mode: the dropdown list expands (animated height), chevron rotates or flips.
  - Color: picker area expands; the swatch gets a glow on hover.
  - Bind mode: row text pulses (`Press a key…`).

### 4.7 Interaction
- **Module row:** LMB = toggle, RMB = expand/collapse settings, MMB = bind mode (next key binds it,
  ESC/DELETE/BACKSPACE = unbind). Hovering shows the description in the tooltip.
- **Panel header:** LMB-drag = move, RMB = collapse the column.
- **Mouse wheel** over a panel scrolls it when its content is taller than the screen.
- **Slider:** drag. Shift+LMB (or MMB) = type a value; Enter commits.
  Arrow keys while hovered = ±step (if step is 0, ±1 % of the range).
- **Color:** click the swatch → expand. Drag in the SV square, hue bar, alpha bar. Click the hex field to edit it.
  `Copy`/`Paste` use the clipboard (`minecraft.keyboardHandler`).
- **Pickers:** click the row (`Blocks  3 selected ⌄`) → expand with a search box + ~6-row scroll list of all registry
  entries (`BuiltInRegistries.BLOCK / ITEM`, enchantments from the registry access). Click toggles selection.
  Selected entries are sorted first and marked with the accent.
- **EntityFilter:** expands to one bool row per key, each with a small color swatch (click the swatch → the same inline color picker).
- **Ctrl+F:** search field at the top center; filters modules across all panels by name (case-insensitive).
- **ESC** closes the GUI (with the close animation). The GUI doesn't pause the game (`isPauseScreen() = false`).
- A small help line in the bottom-left, dim text, in our font.

### 4.8 ClickGuiModule / customization
- Add `CLIENT` to `Module.Category`. Register `ClickGuiModule` in `ModuleManager.init()`.
- Toggling it opens the screen (and immediately sets `enabled` back to false, so it never shows as "enabled").
- Default key: **Right Shift**.
- Settings (use the existing data holders so they are edited and persisted like everything else):
  - `Accent` ColorPicker, default `0xFF6C8CFF` (soft blue)
  - `Scale` Slider 1.0–3.0, step 0.25, default 1.5
  - `Radius` Slider 0–5, step 0.5, default 2.5
  - `Glow` Slider 0–1, step 0.05, default 0.6
  - `Anim Speed` Slider 0.25–3, step 0.25, default 1
  - `Background` ModeButton `Blur`, `Dim`, `None` (default Blur)
  - `Outline` ToggleButton (thin outline around panels), default true
  - `Descriptions` ToggleButton (show tooltips), default true
- Base palette (not user-editable, keep it simple): panel background `0xF0121216`, row `0xFF18181D`, row hover `0xFF1F1F26`,
  text `0xFFE6E6EB`, dim text `0xFF8A8A96`, setting background `0xFF141418`.

### 4.9 Keybinds
- Add `public int key = -1;` to `Module` (GLFW key code, -1 = none).
- In `EntryPoint`'s `END_CLIENT_TICK` (or the existing tick handler): if `client.screen == null`, for every module with
  `key != -1` detect a **fresh press** (was up last tick, down now; keep the previous state in a map) → `toggle()`.
  Don't toggle while chat or any other screen is open.

### 4.10 Persistence (`config/Config.java`)
- File: `<gameDir>/config/0smasclient.json` (`FabricLoader.getInstance().getConfigDir()`). Pretty-printed Gson.
- Shape:
  ```json
  { "version": 1,
    "modules": { "KillAura": { "enabled": false, "key": -1,
                  "settings": { "Range": 4.5, "Mode": "Single", "Players": {"filters":{...},"colors":{...}} } } },
    "panels":  { "COMBAT": { "x": 8, "y": 8, "collapsed": false } } }
  ```
- Settings are keyed by `getLabel()`. If a module has duplicate labels, suffix `#2`, `#3`.
  Type-specific encoding: bool → boolean, slider → number, mode → mode **name** (fall back to index 0 if unknown),
  color → hex string `#AARRGGBB`, text → string, block/item → registry id strings (item: `{id: count}`),
  enchant → `{id: level}`, entity filter → the two maps.
- Load right after `ModuleManager.init()`. Apply values through the setters (so callbacks fire). If a module is enabled,
  call `toggle()` so `onEnable` runs, but guard with try/catch: the player/world is null at startup, so a module may throw.
  Keep the load order robust.
- Save on GUI close, on client stop (`ClientLifecycleEvents.CLIENT_STOPPING`), and after rebinding.
- Ignore unknown/missing keys; a bad file must never crash the game (log it and keep the defaults).

---

## 5. Implementation plan (do these in order, commit after each step)

Build: `./gradlew build` (Windows: `.\gradlew.bat build`). Run: `./gradlew runClient`.
The user tests visually in-game. After a visual step, tell them what to look at.
Commit on `master` is fine (that's what the repo does). End commit messages with the attribution lines your harness gives you.

- [ ] **0. SDF spike.** `UiPipelines` + `ui_sdf` shaders + `SdfRectState` + a tiny `Ui.round/glow`. Temporary: a test
      screen opened with RShift draws a rounded rect with glow at scale 1.5. Verify position, AA, blending, independent scale.
      If it fails after a real effort → **stop and ask the user** (see §4.4).
- [ ] **1. Font.** Add Inter + `font/ui.json`, `Ui.text/textWidth`. Verify it renders crisp at scale 1, 1.5, 2, 3.
- [ ] **2. Pure logic + tests.** Add JUnit 5 to `build.gradle` (`testImplementation 'org.junit.jupiter:junit-jupiter:5.x'`,
      `test { useJUnitPlatform() }`). Write `Anim` + `ColorUtil` test-first. `./gradlew test` must pass.
- [ ] **3. Data holder getters.** `Slider` min/max/step/default, `ModeButton.getModes()`, `TextBox` getters, `Module.key`,
      `Category.CLIENT`. Add nothing else to modules.
- [ ] **4. Screen + Panel + ModuleRow.** Scaling, dragging, collapse, scroll, toggle/hover/expand animations, tooltip,
      help line, open/close animation, background mode. `ClickGuiModule` + `Theme`.
- [ ] **5. Keybinds.** Tick-based key handling, RShift opens the GUI, `BindWidget`, MMB bind mode.
- [ ] **6. Setting widgets.** Bool → Slider → Mode → Color (full picker) → Text → EntityFilter → ListPicker.
      Build and look at each one in-game before moving on.
- [ ] **7. Search** (Ctrl+F).
- [ ] **8. Config persistence** + round-trip unit test for the pure JSON mapping part (keep the MC-free parts testable).
- [ ] **9. Polish pass.** Compare against the reference description (§2): compact, crisp, consistent spacing; animations
      smooth at 30 and 240 FPS; the MC GUI scale (Options → Video) changes nothing; tiny window (854×480) and 4K both usable.
- [ ] **10. Cleanup.** Remove the spike test screen, check for leftover debug code, final `./gradlew build`, update this
      file (all boxes ticked) and tell the user what's done.

### Definition of done
- RShift opens a small, clean column GUI with all 6 categories (Combat, Movement, Visual, Misc, Donut, Client).
- Every setting type used by the modules is editable in the GUI and animates.
- Soft accent glow + rounded corners via the SDF shader; crisp TTF text.
- GUI size is independent of Minecraft's GUI scale and adjustable in the ClickGui module.
- Everything persists across restarts; a broken config never crashes the game.
- `./gradlew build` and `./gradlew test` pass.
