package dev.thri.mixin;

import dev.thri.Thri;
import dev.thri.event.events.RenderWorldEvent;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Frustum;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;DDD)V"),
            require = 0)
    private void thri$postEntities(net.minecraft.client.render.RenderTickCounter counter,
                                   boolean renderBlockOutline, Camera camera,
                                   net.minecraft.client.render.GameRenderer gameRenderer,
                                   net.minecraft.client.render.LightmapTextureManager lightmap,
                                   org.joml.Matrix4f positionMatrix, org.joml.Matrix4f projectionMatrix,
                                   CallbackInfo ci) {
        Vec3d p = camera.getPos();
        MatrixStack stack = new MatrixStack();
        stack.multiplyPositionMatrix(positionMatrix);
        Thri.BUS.post(new RenderWorldEvent(stack, counter.getTickDelta(true), p.x, p.y, p.z));
    }
}
