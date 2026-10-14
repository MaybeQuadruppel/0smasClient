package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.Trajectories;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.*;

public class PlayerESP extends Module {
    public static PlayerESP INSTANCE;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("newbridge/PlayerESP");
    private boolean loggedVisibilityError = false;

    public float range = 128;
    public double outlineWidth = 2.0;
    public double tracerWidth = 2.0;
    public String renderMode = "Both";
    public boolean renderTracers = false;
    public boolean distanceColors = false;
    public boolean splitVisibility = false;
    public boolean teamColorOverride = true; // NEU: Steuert, ob Teamfarben invertiert/überschrieben werden
    public final ColorPicker occludedColor = new ColorPicker("Occluded Color", 0xFFFF3B3B, null);
    public boolean glow = false;
    public double glowStrength = 1.0;
    public EntityFilterPicker targetPicker;

    // --- SKELETON CACHES & FIELDS ---
    private final ChamsBufferSource bufferSource = new ChamsBufferSource();
    private final List<Entity> targets = new ArrayList<>();
    private int[] targetColors = new int[64];
    private static final Map<Entity, LivingEntityRenderState> STATE_CACHE = new WeakHashMap<>();
    private static final Map<ModelPart, Map<String, ModelPart>> CHILDREN_CACHE = new IdentityHashMap<>();
    private static final Map<ModelPart, Vector3f> LOCAL_CENTER_CACHE = new IdentityHashMap<>();
    private static final Map<String, Boolean> OVERLAY_CACHE = new HashMap<>();

    private static final PoseStack CALC_STACK = new PoseStack();
    private static final Vector3f SCRATCH_VEC = new Vector3f();
    private static final Vector3f SCRATCH_NORMAL = new Vector3f();

    private static final Field CHILDREN_FIELD;
    private static final Field CUBES_FIELD;

    static {
        try {
            CHILDREN_FIELD = ModelPart.class.getDeclaredField("children");
            CHILDREN_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }

        Field cubesField = null;
        for (String name : new String[]{"cubes", "cuboids"}) {
            try {
                cubesField = ModelPart.class.getDeclaredField(name);
                cubesField.setAccessible(true);
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        CUBES_FIELD = cubesField;
    }

    public PlayerESP() {
        super("EntityESP", "Lets you See Entities by their Threatlevels", Category.VISUAL);
        INSTANCE = this;

        this.targetPicker = new EntityFilterPicker("Targets");
        this.settings.add(this.targetPicker.withDescription("Selects which entity types to highlight with ESP."));

        this.settings.add(new Slider("Range", 1.0, 128.0, (double) range, val -> range = val.floatValue())
                .withDescription("Sets the maximum distance at which entities are highlighted."));

        this.settings.add(new Slider("Outline Width", 0.5, 10.0, outlineWidth, val -> outlineWidth = val)
                .withDescription("Sets the line thickness for entity box outlines & skeleton lines."));

        this.settings.add(new Slider("Tracer Width", 0.5, 10.0, tracerWidth, val -> tracerWidth = val)
                .withDescription("Sets the line thickness for tracers."));

        List<String> modes = List.of("Fill", "Outline", "Both", "Skeleton", "None");
        this.settings.add(new ModeButton("Mode", modes, modes.indexOf(renderMode), val -> renderMode = val)
                .withDescription("Selects rendering style (Fill, Outline, Both, Skeleton, None)."));

        this.settings.add(new ToggleButton("Distance Colors", distanceColors, val -> distanceColors = val)
                .withDescription("Uses distance-based colors for all ESP modes."));

        this.settings.add(new ToggleButton("Team Color Override", teamColorOverride, val -> teamColorOverride = val)
                .withDescription("Inverts ESP colors for teammates."));

        this.settings.add(new ToggleButton("Tracers", renderTracers, val -> renderTracers = val)
                .withDescription("Draws tracer lines to entities."));

        this.settings.add(new ToggleButton("Split Visibility", splitVisibility, val -> splitVisibility = val)
                .withDescription("Splits every line/face exactly where terrain blocks the view: visible part in normal color, hidden part in occluded color. Entities never count as obstacles."));
        this.settings.add(occludedColor
                .withDescription("Color for the part of the hitbox blocked by terrain."));

        this.settings.add(new ToggleButton("Glow", glow, val -> glow = val)
                .withDescription("Adds a soft glow halo around all ESP lines."));
        this.settings.add(new Slider("Glow Strength", 0.0, 3.0, glowStrength, val -> glowStrength = val)
                .withDescription("How strong/wide the glow halo is."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    public static PlayerESP getInstance() {
        if (INSTANCE == null) INSTANCE = new PlayerESP();
        return INSTANCE;
    }

    @Subscribe
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.gameRenderer.mainCamera() == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = client.gameRenderer.mainCamera();
        double camX = camera.position().x;
        double camY = camera.position().y;
        double camZ = camera.position().z;

        float tickDelta = event.getTickDelta();

        boolean drawFill = renderMode.equals("Fill") || renderMode.equals("Both");
        boolean drawOutline = renderMode.equals("Outline") || renderMode.equals("Both");
        boolean drawSkeleton = renderMode.equals("Skeleton");

        collectTargets(client);
        if (targets.isEmpty()) return;

        // Split Visibility: jede Kante / Flaeche wird einzeln gegen Bloecke geraycastet.
        // Entities zaehlen dabei nie als Hindernis (ClipContext prueft nur Block-Kollision).
        boolean split = splitVisibility && !renderMode.equals("None");
        beginSplitFrame(split ? client.level : null, camX, camY, camZ, client.player);
        int occColor = occludedColor.getColor() | 0xFF000000;

        float startX = 0f, startY = 0f, startZ = 0f;
        if (renderTracers) {
            float pitch = camera.xRot();
            float yaw = camera.yRot();
            float f = (float) Math.PI / 180.0F;

            float dirX = -((float) Math.sin(yaw * f)) * ((float) Math.cos(pitch * f));
            float dirY = -((float) Math.sin(pitch * f));
            float dirZ = ((float) Math.cos(yaw * f)) * ((float) Math.cos(pitch * f));

            float offset = 50.0f;
            startX = dirX * offset;
            startY = dirY * offset;
            startZ = dirZ * offset;
        }

        try {
            // --- FILLS ---
            if (drawFill) {
                VertexConsumer fillConsumer = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());
                for (int i = 0; i < targets.size(); i++) {
                    Entity living = targets.get(i);
                    this.split.beginTape(living.getId() * 4);
                    renderRotatedBox(poseStack, fillConsumer, living, tickDelta, camX, camY, camZ,
                            halfAlpha(targetColors[i]), halfAlpha(occColor), true, (float) outlineWidth);
                    this.split.endTape();
                }
            }

            // --- OUTLINES ---
            if (drawOutline) {
                VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
                for (int i = 0; i < targets.size(); i++) {
                    Entity living = targets.get(i);
                    this.split.beginTape(living.getId() * 4 + 1);
                    renderRotatedBox(poseStack, lineConsumer, living, tickDelta, camX, camY, camZ,
                            targetColors[i], occColor, false, (float) outlineWidth);
                    this.split.endTape();
                }
            }

            // --- SKELETON ---
            if (drawSkeleton) {
                VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
                for (int i = 0; i < targets.size(); i++) {
                    if (!(targets.get(i) instanceof LivingEntity living)) continue;
                    int color = targetColors[i];
                    EntityRenderer<?, ?> renderer = client.getEntityRenderDispatcher().getRenderer(living);
                    if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) continue;
                    EntityModel<?> model = livingRenderer.getModel();

                    LivingEntityRenderer rawRenderer = (LivingEntityRenderer) renderer;
                    LivingEntityRenderState state = STATE_CACHE.computeIfAbsent(living,
                            e -> (LivingEntityRenderState) rawRenderer.createRenderState());

                    rawRenderer.extractRenderState(living, state, tickDelta);
                    ((EntityModel) model).setupAnim(state);

                    Vec3 entityPos = new Vec3(
                            Mth.lerp(tickDelta, living.xo, living.getX()),
                            Mth.lerp(tickDelta, living.yo, living.getY()),
                            Mth.lerp(tickDelta, living.zo, living.getZ())
                    );

                    PoseStack calcStack = CALC_STACK;
                    calcStack.last().pose().identity();
                    calcStack.last().normal().identity();

                    calcStack.translate(entityPos.x - camX, entityPos.y - camY, entityPos.z - camZ);

                    float yaw = Mth.rotLerp(tickDelta, living.yBodyRotO, living.yBodyRot);
                    calcStack.rotate(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yaw));

                    calcStack.scale(-1.0F, -1.0F, 1.0F);
                    calcStack.translate(0.0F, -1.501F, 0.0F);

                    Matrix4f matrix = poseStack.last().pose();
                    Matrix3f normalMatrix = poseStack.last().normal();

                    skeletonOccColor = occColor;
                    this.split.beginTape(living.getId() * 4 + 2);
                    renderSkeletonPart(calcStack, "root", model.root(), lineConsumer, matrix, normalMatrix, color, (float) outlineWidth, null);
                    this.split.endTape();
                }
            }

            // --- TRACERS ---
            if (renderTracers) {
                VertexConsumer tracerConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
                for (int i = 0; i < targets.size(); i++) {
                    Entity living = targets.get(i);
                    int color = targetColors[i];
                    double x = Mth.lerp(tickDelta, living.xo, living.getX()) - camX;
                    double y = Mth.lerp(tickDelta, living.yo, living.getY()) - camY;
                    double z = Mth.lerp(tickDelta, living.zo, living.getZ()) - camZ;

                    float targetX = (float) x;
                    float targetY = (float) (y + living.getBbHeight() / 2f);
                    float targetZ = (float) z;

                    Matrix4f matrix = poseStack.last().pose();
                    Matrix3f normalMatrix = poseStack.last().normal();

                    line(matrix, normalMatrix, tracerConsumer, startX, startY, startZ, targetX, targetY, targetZ, color, (float) tracerWidth);
                }
            }
        } finally {
            bufferSource.uploadAndDraw();
            targets.clear();
            beginSplitFrame(null, 0, 0, 0, null);
        }
    }

    private void collectTargets(Minecraft client) {
        targets.clear();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !entity.isAlive()) continue;

            boolean isTrajTarget = (Trajectories.targetedEntity == entity);
            if (!isTrajTarget && client.player.distanceToSqr(entity) > range * range) continue;

            String filterKey = getFilterKey(entity);
            boolean isEnabledInPicker = targetPicker != null
                    && !targetPicker.isEntityExcluded(entity.getType())
                    && ((filterKey != null && targetPicker.isFilterEnabled(filterKey))
                    || targetPicker.isEntitySelected(entity.getType()));
            if (!isTrajTarget && !isEnabledInPicker) continue;

            if (targets.size() == targetColors.length) {
                targetColors = Arrays.copyOf(targetColors, targetColors.length * 2);
            }
            targetColors[targets.size()] = getEntityColor(client.player, entity, filterKey, 1.0f);
            targets.add(entity);
        }
    }

    private static int halfAlpha(int argb) {
        return (((argb >>> 24) >> 1) << 24) | (argb & 0x00FFFFFF);
    }

    private int getEntityColor(Player clientPlayer, Entity target, String filterKey, float alphaMultiplier) {
        int color;
        if (distanceColors) {
            color = getDistanceColor(clientPlayer, target, alphaMultiplier);
        } else {
            color = getAdjustedColor(targetPicker.getColor(target.getType(), filterKey), alphaMultiplier);
        }

        // Respektiert nun den Toggle-Status im UI!
        if (teamColorOverride && TeamUtils.isTeammate(target)) {
            color = invertColor(color);
        }

        return color;
    }

    private int invertColor(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = 255 - ((argb >> 16) & 0xFF);
        int g = 255 - ((argb >> 8) & 0xFF);
        int b = 255 - (argb & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderSkeletonPart(PoseStack calcStack, String name, ModelPart part,
                                    VertexConsumer consumer, Matrix4f baseMatrix, Matrix3f normalMatrix,
                                    int color, float lineWidth, Vec3 parentPos) {
        if (isOverlayPart(name)) return;

        calcStack.pushPose();
        part.translateAndRotate(calcStack);

        SCRATCH_VEC.set(0, 0, 0).mulPosition(calcStack.last().pose());
        Vec3 currentPivot = new Vec3(SCRATCH_VEC.x(), SCRATCH_VEC.y(), SCRATCH_VEC.z());

        Vector3f localCenter = getLocalCenter(part);
        Vec3 currentCenter = currentPivot;
        if (localCenter.lengthSquared() > 1.0E-6f) {
            SCRATCH_VEC.set(localCenter).mulPosition(calcStack.last().pose());
            currentCenter = new Vec3(SCRATCH_VEC.x(), SCRATCH_VEC.y(), SCRATCH_VEC.z());
        }

        if (parentPos != null && parentPos.distanceToSqr(currentPivot) > 1.0E-4) {
            segLine(baseMatrix, normalMatrix, consumer,
                    (float) parentPos.x, (float) parentPos.y, (float) parentPos.z,
                    (float) currentPivot.x, (float) currentPivot.y, (float) currentPivot.z,
                    color, skeletonOccColor, lineWidth);
        }

        if (currentPivot.distanceToSqr(currentCenter) > 1.0E-4) {
            segLine(baseMatrix, normalMatrix, consumer,
                    (float) currentPivot.x, (float) currentPivot.y, (float) currentPivot.z,
                    (float) currentCenter.x, (float) currentCenter.y, (float) currentCenter.z,
                    color, skeletonOccColor, lineWidth);
        }

        Vec3 nextParentPos = (currentCenter != currentPivot) ? currentCenter : currentPivot;
        Map<String, ModelPart> children = getModelPartChildren(part);

        Vec3 bodyCenterOverride = null;

        ModelPart bodyPart = children.get("body");
        if (bodyPart == null) bodyPart = children.get("body0");
        if (bodyPart == null) bodyPart = children.get("spine");

        if (bodyPart != null) {
            calcStack.pushPose();
            bodyPart.translateAndRotate(calcStack);

            SCRATCH_VEC.set(0, 0, 0).mulPosition(calcStack.last().pose());
            bodyCenterOverride = new Vec3(SCRATCH_VEC.x(), SCRATCH_VEC.y(), SCRATCH_VEC.z());

            Vector3f bCenter = getLocalCenter(bodyPart);
            if (bCenter.lengthSquared() > 1.0E-6f) {
                SCRATCH_VEC.set(bCenter).mulPosition(calcStack.last().pose());
                bodyCenterOverride = new Vec3(SCRATCH_VEC.x(), SCRATCH_VEC.y(), SCRATCH_VEC.z());
            }
            calcStack.popPose();
        }

        for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
            String childName = entry.getKey();
            ModelPart childPart = entry.getValue();

            Vec3 childParent = nextParentPos;

            if (name.equals("root") && childPart == bodyPart) {
                childParent = null;
            } else if (bodyCenterOverride != null && !isOverlayPart(childName)) {
                childParent = bodyCenterOverride;
            }

            renderSkeletonPart(calcStack, childName, childPart, consumer, baseMatrix, normalMatrix, color, lineWidth, childParent);
        }

        calcStack.popPose();
    }

    private boolean isOverlayPart(String name) {
        if (name == null) return false;
        return OVERLAY_CACHE.computeIfAbsent(name, this::computeIsOverlayPart);
    }

    private boolean computeIsOverlayPart(String name) {
        String lower = name.toLowerCase();
        return lower.contains("jacket") || lower.contains("sleeve") || lower.contains("pants")
                || lower.contains("overlay") || lower.contains("outer") || lower.contains("cape")
                || lower.contains("cloak") || lower.contains("ear") || lower.contains("mane")
                || lower.contains("saddle") || lower.contains("rein") || lower.contains("bridle")
                || lower.contains("mouth") || lower.contains("tail") || lower.contains("line")
                || lower.contains("wrap") || lower.contains("hair") || lower.contains("armor")
                || lower.contains("chest") || lower.contains("bag") || lower.contains("hat");
    }

    @SuppressWarnings("unchecked")
    private Vector3f getLocalCenter(ModelPart part) {
        return LOCAL_CENTER_CACHE.computeIfAbsent(part, p -> {
            try {
                if (CUBES_FIELD == null) return new Vector3f(0, 0, 0);
                List<ModelPart.Cube> cubes = (List<ModelPart.Cube>) CUBES_FIELD.get(p);
                if (cubes == null || cubes.isEmpty()) return new Vector3f(0, 0, 0);

                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

                for (ModelPart.Cube cube : cubes) {
                    minX = Math.min(minX, cube.minX); maxX = Math.max(maxX, cube.maxX);
                    minY = Math.min(minY, cube.minY); maxY = Math.max(maxY, cube.maxY);
                    minZ = Math.min(minZ, cube.minZ); maxZ = Math.max(maxZ, cube.maxZ);
                }

                return new Vector3f(
                        ((minX + maxX) / 2.0f) * 0.0625f,
                        ((minY + maxY) / 2.0f) * 0.0625f,
                        ((minZ + maxZ) / 2.0f) * 0.0625f
                );
            } catch (Exception e) {
                return new Vector3f(0, 0, 0);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, ModelPart> getModelPartChildren(ModelPart part) {
        return CHILDREN_CACHE.computeIfAbsent(part, p -> {
            try {
                return (Map<String, ModelPart>) CHILDREN_FIELD.get(p);
            } catch (Exception e) {
                return Collections.emptyMap();
            }
        });
    }

    private int getDistanceColor(Player clientPlayer, Entity target, float alphaMultiplier) {
        double distance = Math.sqrt(clientPlayer.distanceToSqr(target));
        double percent = Math.min(1.0, distance / 60.0);
        int r, g;
        if (percent < 0.33) {
            r = (int) (percent / 0.33 * 255);
            g = 255;
        } else if (percent < 0.66) {
            r = 255;
            g = 255 - (int) ((percent - 0.33) / 0.33 * 90);
        } else {
            r = 255;
            g = 165 - (int) ((percent - 0.66) / 0.34 * 165);
        }
        int a = (int) (255 * alphaMultiplier);
        return (a << 24) | (r << 16) | (g << 8) | 0;
    }

    // Ecken-Indizes: bit0 = x2, bit1 = y2, bit2 = z2
    private static final int[][] BOX_FACES = {
            {0, 1, 5, 4}, // unten  (0,-1,0)
            {6, 7, 3, 2}, // oben   (0, 1,0)
            {0, 2, 3, 1}, // Nord   (0,0,-1)
            {5, 7, 6, 4}, // Sued   (0,0, 1)
            {4, 6, 2, 0}, // West   (-1,0,0)
            {1, 3, 7, 5}  // Ost    ( 1,0,0)
    };
    private static final float[][] BOX_FACE_NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 5}, {5, 4}, {4, 0},
            {2, 3}, {3, 7}, {7, 6}, {6, 2},
            {0, 2}, {1, 3}, {5, 7}, {4, 6}
    };
    private final float[] cornerBuf = new float[24];

    private void renderRotatedBox(PoseStack poseStack, VertexConsumer consumer, Entity entity, float tickDelta,
                                  double camX, double camY, double camZ, int color, int occColor,
                                  boolean isFill, float lineWidth) {
        double x = Mth.lerp(tickDelta, entity.xo, entity.getX()) - camX;
        double y = Mth.lerp(tickDelta, entity.yo, entity.getY()) - camY;
        double z = Mth.lerp(tickDelta, entity.zo, entity.getZ()) - camZ;

        float w = entity.getBbWidth() / 2f;
        float h = entity.getBbHeight();
        float yaw = entity instanceof LivingEntity le ? Mth.lerp(tickDelta, le.yBodyRotO, le.yBodyRot) : 0f;

        // Ecken direkt kamera-relativ berechnen (gleiche Rotation wie vorher Axis.YP.rotationDegrees(-yaw)),
        // damit wir fuer Split-Visibility echte Weltpositionen raycasten koennen.
        float rad = (float) Math.toRadians(-yaw);
        float cos = Mth.cos(rad), sin = Mth.sin(rad);
        float[] c = cornerBuf;
        for (int i = 0; i < 8; i++) {
            float lx = (i & 1) != 0 ? w : -w;
            float ly = (i & 2) != 0 ? h : 0f;
            float lz = (i & 4) != 0 ? w : -w;
            c[i * 3] = (float) x + lx * cos + lz * sin;
            c[i * 3 + 1] = (float) y + ly;
            c[i * 3 + 2] = (float) z - lx * sin + lz * cos;
        }

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        if (isFill) {
            int a = (color >>> 24), r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            for (int f = 0; f < 6; f++) {
                int[] q = BOX_FACES[f];
                float[] ln = BOX_FACE_NORMALS[f];
                float nx = ln[0] * cos + ln[2] * sin;
                float nz = -ln[0] * sin + ln[2] * cos;
                if (split.active()) {
                    splitQuad(matrix, normalMatrix, consumer, c, q, nx, ln[1], nz, color, occColor);
                } else {
                    addQuad(matrix, normalMatrix, consumer,
                            c[q[0] * 3], c[q[0] * 3 + 1], c[q[0] * 3 + 2],
                            c[q[1] * 3], c[q[1] * 3 + 1], c[q[1] * 3 + 2],
                            c[q[2] * 3], c[q[2] * 3 + 1], c[q[2] * 3 + 2],
                            c[q[3] * 3], c[q[3] * 3 + 1], c[q[3] * 3 + 2],
                            nx, ln[1], nz, r, g, b, a);
                }
            }
        } else {
            for (int[] e : BOX_EDGES) {
                segLine(matrix, normalMatrix, consumer,
                        c[e[0] * 3], c[e[0] * 3 + 1], c[e[0] * 3 + 2],
                        c[e[1] * 3], c[e[1] * 3 + 1], c[e[1] * 3 + 2],
                        color, occColor, lineWidth);
            }
        }
    }

    // ===================== SPLIT VISIBILITY =====================
    // Logik in SplitVisibility (headless testbar); hier nur das Zeichnen.

    private final SplitVisibility split = new SplitVisibility();
    private int skeletonOccColor;

    private void beginSplitFrame(Level level, double camX, double camY, double camZ, Entity self) {
        if (level == null) {
            split.end();
            return;
        }
        split.onError(() -> {
            if (!loggedVisibilityError) {
                loggedVisibilityError = true;
                LOG.error("Split Visibility raycast failed, falling back to normal colors");
            }
        });
        split.begin(level, self == null ? null : net.minecraft.world.phys.shapes.CollisionContext.of(self), camX, camY, camZ);
    }

    /** Zeichnet eine Linie; bei Split-Visibility wird sie an den Verdeckungsgrenzen zweifarbig geteilt. */
    private void segLine(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                         float ax, float ay, float az, float bx, float by, float bz,
                         int color, int occColor, float lineWidth) {
        if (!split.active()) {
            line(matrix, normalMatrix, consumer, ax, ay, az, bx, by, bz, color, lineWidth);
            return;
        }
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        split.splitLine(ax, ay, az, bx, by, bz, (t0, t1, visible) ->
                emitSegment(matrix, normalMatrix, consumer, ax, ay, az, dx, dy, dz, t0, t1,
                        visible ? color : occColor, lineWidth));
    }

    private static void emitSegment(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                                    float ax, float ay, float az, float dx, float dy, float dz,
                                    float t0, float t1, int color, float lineWidth) {
        if (t1 - t0 < 1.0E-4f) return;
        line(matrix, normalMatrix, consumer,
                ax + dx * t0, ay + dy * t0, az + dz * t0,
                ax + dx * t1, ay + dy * t1, az + dz * t1,
                color, lineWidth);
    }

    /** Unterteilt eine Box-Flaeche in Zellen und faerbt jede Zelle nach ihrer Sichtbarkeit. */
    private void splitQuad(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                           float[] c, int[] q, float nx, float ny, float nz, int color, int occColor) {
        float p1x = c[q[0] * 3], p1y = c[q[0] * 3 + 1], p1z = c[q[0] * 3 + 2];
        float ux = c[q[1] * 3] - p1x, uy = c[q[1] * 3 + 1] - p1y, uz = c[q[1] * 3 + 2] - p1z;
        float vx = c[q[3] * 3] - p1x, vy = c[q[3] * 3 + 1] - p1y, vz = c[q[3] * 3 + 2] - p1z;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        float vLen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);

        int ns = split.cellsFor(uLen), nt = split.cellsFor(vLen);
        int vr = (color >> 16) & 0xFF, vg = (color >> 8) & 0xFF, vb = color & 0xFF, va = color >>> 24;
        int or = (occColor >> 16) & 0xFF, og = (occColor >> 8) & 0xFF, ob = occColor & 0xFF, oa = occColor >>> 24;

        for (int i = 0; i < ns; i++) {
            float s0 = (float) i / ns, s1 = (float) (i + 1) / ns, sm = (s0 + s1) * 0.5f;
            for (int j = 0; j < nt; j++) {
                float t0 = (float) j / nt, t1 = (float) (j + 1) / nt, tm = (t0 + t1) * 0.5f;
                boolean vis = split.cellVisible(p1x + ux * sm + vx * tm, p1y + uy * sm + vy * tm, p1z + uz * sm + vz * tm);
                addQuad(matrix, normalMatrix, consumer,
                        p1x + ux * s0 + vx * t0, p1y + uy * s0 + vy * t0, p1z + uz * s0 + vz * t0,
                        p1x + ux * s1 + vx * t0, p1y + uy * s1 + vy * t0, p1z + uz * s1 + vz * t0,
                        p1x + ux * s1 + vx * t1, p1y + uy * s1 + vy * t1, p1z + uz * s1 + vz * t1,
                        p1x + ux * s0 + vx * t1, p1y + uy * s0 + vy * t1, p1z + uz * s0 + vz * t1,
                        nx, ny, nz,
                        vis ? vr : or, vis ? vg : og, vis ? vb : ob, vis ? va : oa);
            }
        }
    }

    private String getFilterKey(Entity entity) {
        if (entity instanceof Player) return "Players";
        if (entity instanceof ArmorStand) return "ArmorStands";
        if (entity instanceof Enemy) return "Hostiles";
        if (entity instanceof Animal) return "Animals";
        if (entity instanceof Villager || entity instanceof WanderingTrader) return "NPCs";
        return null;
    }

    private int getAdjustedColor(int argb, float alphaMultiplier) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        if (a == 102 || a <= 5) {
            a = 255;
        }

        a = (int) (a * alphaMultiplier);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void addQuad(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4,
                                float nx, float ny, float nz, int r, int g, int b, int a) {
        Vector3f normal = SCRATCH_NORMAL.set(nx, ny, nz).mul(normalMatrix);

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color, float lineWidth) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        line(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, r, g, b, a, lineWidth);
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int a, float lineWidth) {
        PlayerESP esp = INSTANCE;
        if (esp != null && esp.glow && esp.glowStrength > 0.001 && a > 0) {
            float strength = (float) esp.glowStrength;
            int haloPasses = 3;
            for (int i = haloPasses; i >= 1; i--) {
                float haloWidth = lineWidth + i * 2.5f * strength;
                float haloAlphaMul = Math.min(1f, strength) * (0.30f / i);
                int haloA = (int) (a * haloAlphaMul);
                if (haloA > 0) {
                    drawLineSegment(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, r, g, b, haloA, haloWidth);
                }
            }
        }
        drawLineSegment(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, r, g, b, a, lineWidth);
    }

    private static void drawLineSegment(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                                        float x1, float y1, float z1, float x2, float y2, float z2,
                                        int r, int g, int b, int a, float lineWidth) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;

        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len != 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dy = 1.0f;
        }
        SCRATCH_NORMAL.set(dx, dy, dz).mul(normalMatrix);

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(SCRATCH_NORMAL.x(), SCRATCH_NORMAL.y(), SCRATCH_NORMAL.z()).setLineWidth(lineWidth);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(SCRATCH_NORMAL.x(), SCRATCH_NORMAL.y(), SCRATCH_NORMAL.z()).setLineWidth(lineWidth);
    }
}
