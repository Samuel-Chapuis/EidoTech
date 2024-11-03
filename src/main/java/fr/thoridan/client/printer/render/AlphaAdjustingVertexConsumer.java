package fr.thoridan.client.printer.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class AlphaAdjustingVertexConsumer implements VertexConsumer {
    private final VertexConsumer inner;
    private final float alpha;

    public AlphaAdjustingVertexConsumer(VertexConsumer inner, float alpha) {
        this.inner = inner;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        return inner.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        // Adjust the alpha value
        int adjustedAlpha = (int) (this.alpha * 255);
        return inner.color(red, green, blue, adjustedAlpha);
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        return inner.uv(u, v);
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return inner.overlayCoords(u, v);
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return inner.uv2(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return inner.normal(x, y, z);
    }

    @Override
    public void endVertex() {
        inner.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        inner.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        inner.unsetDefaultColor();
    }
}
