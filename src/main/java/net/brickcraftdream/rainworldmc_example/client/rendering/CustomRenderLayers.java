package net.brickcraftdream.rainworldmc_example.client.rendering;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public class CustomRenderLayers extends RenderLayer {

    private CustomRenderLayers(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }


    /**
     * Creates a custom translucent render layer with a specified texture. Couldn't find a way to do this using vanilla RenderLayers, so I just implemented my own
     * @param texture the texture to use for the render layer, defined by an {@link Identifier}
     * @return a new RenderLayer instance with the specified texture and translucent properties
     */
    public static RenderLayer translucent(Identifier texture) {
        return RenderLayer.of(
                "rendertype_" + texture.getPath(),
                VertexFormats.POSITION_COLOR,
                VertexFormat.DrawMode.QUADS,
                256,
                true,
                true,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(RenderPhase.ShaderProgram.POSITION_COLOR_TEXTURE_LIGHTMAP_PROGRAM)
                        .texture(new RenderPhase.Texture(texture, false, false))
                        .transparency(RenderPhase.Transparency.TRANSLUCENT_TRANSPARENCY)
                        .cull(RenderPhase.DISABLE_CULLING)
                        .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                        .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                        .writeMaskState(RenderPhase.COLOR_MASK)
                        .target(RenderPhase.TRANSLUCENT_TARGET)
                        .build(true)
        );
    }
}
