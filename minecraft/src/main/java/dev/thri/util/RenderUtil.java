package dev.thri.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

public final class RenderUtil {
    private RenderUtil() {}

    public static void drawBoxOutline(MatrixStack m, Box box, float r, float g, float b, float a, double camX, double camY, double camZ) {
        float x1 = (float)(box.minX - camX), y1 = (float)(box.minY - camY), z1 = (float)(box.minZ - camZ);
        float x2 = (float)(box.maxX - camX), y2 = (float)(box.maxY - camY), z2 = (float)(box.maxZ - camZ);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(1.5f);

        Matrix4f mat = m.peek().getPositionMatrix();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // 12 edges
        line(bb, mat, x1,y1,z1, x2,y1,z1, r,g,b,a);
        line(bb, mat, x2,y1,z1, x2,y1,z2, r,g,b,a);
        line(bb, mat, x2,y1,z2, x1,y1,z2, r,g,b,a);
        line(bb, mat, x1,y1,z2, x1,y1,z1, r,g,b,a);
        line(bb, mat, x1,y2,z1, x2,y2,z1, r,g,b,a);
        line(bb, mat, x2,y2,z1, x2,y2,z2, r,g,b,a);
        line(bb, mat, x2,y2,z2, x1,y2,z2, r,g,b,a);
        line(bb, mat, x1,y2,z2, x1,y2,z1, r,g,b,a);
        line(bb, mat, x1,y1,z1, x1,y2,z1, r,g,b,a);
        line(bb, mat, x2,y1,z1, x2,y2,z1, r,g,b,a);
        line(bb, mat, x2,y1,z2, x2,y2,z2, r,g,b,a);
        line(bb, mat, x1,y1,z2, x1,y2,z2, r,g,b,a);

        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void drawLine(MatrixStack m, double x1, double y1, double z1, double x2, double y2, double z2,
                                float r, float g, float b, float a, double camX, double camY, double camZ) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(1.5f);
        Matrix4f mat = m.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        line(bb, mat,
                (float)(x1 - camX),(float)(y1 - camY),(float)(z1 - camZ),
                (float)(x2 - camX),(float)(y2 - camY),(float)(z2 - camZ),
                r,g,b,a);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void line(BufferBuilder bb, Matrix4f m,
                             float x1,float y1,float z1,float x2,float y2,float z2,
                             float r,float g,float b,float a) {
        bb.vertex(m, x1,y1,z1).color(r,g,b,a);
        bb.vertex(m, x2,y2,z2).color(r,g,b,a);
    }
}
