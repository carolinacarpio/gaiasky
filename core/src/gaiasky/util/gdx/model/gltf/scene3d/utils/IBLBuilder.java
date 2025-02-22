/*
 * This file is part of the gdx-gltf (https://github.com/mgsx-dev/gdx-gltf) library,
 * under the APACHE 2.0 license. We have possibly modified parts of this file so that
 * 32-bit integer indices are supported, instead of only 16-bit short ones.
 */

package gaiasky.util.gdx.model.gltf.scene3d.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.Cubemap.CubemapSide;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.FrameBufferCubemap;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import gaiasky.util.gdx.model.gltf.scene3d.attributes.PBRCubemapAttribute;
import gaiasky.util.gdx.model.gltf.scene3d.scene.SceneSkybox;

public class IBLBuilder implements Disposable
{
	public static IBLBuilder createOutdoor(DirectionalLight sun) {
		IBLBuilder ibl = new IBLBuilder();
		
		ibl.nearGroundColor.set(.5f, .45f, .4f, 1);
		ibl.farGroundColor.set(.3f, .25f, .2f, 1);
		ibl.nearSkyColor.set(.7f, .8f, 1f, 1);
		ibl.farSkyColor.set(.9f, .95f, 1f, 1);
		
		Light light = new Light();
		light.direction.set(sun.direction).nor();
		light.color.set(sun.color);
		light.exponent = 30f;
		ibl.lights.add(light);
		
		return ibl;
	}
	
	public static IBLBuilder createIndoor(DirectionalLight sun) {
		IBLBuilder ibl = new IBLBuilder();
		
		Color tint = new Color(1f, .9f, .8f, 1).mul(.3f);
		
		ibl.nearGroundColor.set(tint).mul(.7f);
		ibl.farGroundColor.set(tint);
		ibl.farSkyColor.set(tint);
		ibl.nearSkyColor.set(tint).mul(2f);
		
		Light light = new Light();
		light.direction.set(sun.direction).nor();
		light.color.set(1f, .5f, 0f, 1f).mul(.3f);
		light.exponent = 3f;
		ibl.lights.add(light);
		
		return ibl;
	}
	
	public static IBLBuilder createCustom(DirectionalLight sun) {
		IBLBuilder ibl = new IBLBuilder();
		
		Light light = new Light();
		light.direction.set(sun.direction).nor();
		light.color.set(sun.color);
		light.exponent = 100f;
		ibl.lights.add(light);
		
		return ibl;
	}
	
	public final Color nearGroundColor = new Color();
	public final Color farGroundColor = new Color();
	public final Color nearSkyColor = new Color();
	public final Color farSkyColor = new Color();
	
	public final Array<Light> lights = new Array<Light>();
	
	public boolean renderSun = true;
	public boolean renderGradient = true;
	
	private final ShaderProgram sunShader;
	private final ShapeRenderer shapes;
	private final ShapeRenderer sunShapes;
	
	private IBLBuilder() {
		shapes = new ShapeRenderer(20);
		shapes.getProjectionMatrix().setToOrtho2D(0, 0, 1, 1);

		sunShader = new ShaderProgram(
				Gdx.files.classpath("net/mgsx/gltf/shaders/ibl-sun.vs.glsl"), 
				Gdx.files.classpath("net/mgsx/gltf/shaders/ibl-sun.fs.glsl"));
		if(!sunShader.isCompiled()) throw new GdxRuntimeException(sunShader.getLog());
		
		sunShapes = new ShapeRenderer(20, sunShader);
		sunShapes.getProjectionMatrix().setToOrtho2D(0, 0, 1, 1);
	}
	
	@Override
	public void dispose() {
		sunShader.dispose();
		sunShapes.dispose();
		shapes.dispose();
	}
	
	/**
	 * Create an environment map, to be used with {@link SceneSkybox}
	 * @param size base size (width and height) for generated cubemap
	 * @return generated cubemap, caller is responsible to dispose it when no longer used.
	 */
	public Cubemap buildEnvMap(int size){
		FrameBufferCubemap fbo = new FrameBufferCubemap(Format.RGBA8888, size, size, false){
			@Override
			protected void disposeColorTexture(Cubemap colorTexture) {
			}
		};
		fbo.begin();
		while(fbo.nextSide()){
			Gdx.gl.glClearColor(0, 0, 0, 0);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
			CubemapSide side = fbo.getSide();
			renderGradient(side, 0);
			renderLights(side, false);
		}
		fbo.end();
		Cubemap map = fbo.getColorBufferTexture();
		fbo.dispose();
		return map;
	}
	
	/**
	 * Creates an irradiance map, to be used with {@link PBRCubemapAttribute#DiffuseEnv}
	 * @param size base size (width and height) for generated cubemap
	 * @return generated cubemap, caller is responsible to dispose it when no longer used.
	 */
	public Cubemap buildIrradianceMap(int size){
		
		FrameBufferCubemap fbo = new FrameBufferCubemap(Format.RGBA8888, size, size, false){
			@Override
			protected void disposeColorTexture(Cubemap colorTexture) {
			}
		};
		
		fbo.begin();
		while(fbo.nextSide()){
			Gdx.gl.glClearColor(0, 0, 0, 0);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
			CubemapSide side = fbo.getSide();
			renderGradient(side, 0.5f);
			renderLights(side, true);
		}
		fbo.end();
		Cubemap map = fbo.getColorBufferTexture();
		fbo.dispose();
		return map;
	}

	/**
	 * Creates an radiance map, to be used with {@link PBRCubemapAttribute#SpecularEnv}
	 * generated cubemap contains mipmaps in order to perform roughness in PBR shading
	 * @param mipMapLevels how many mipmaps level, eg. 10 levels produce a 1024x1024 cubemap with mipmaps.
	 * @return generated cubemap, caller is responsible to dispose it when no longer used.
	 */
	public Cubemap buildRadianceMap(final int mipMapLevels){
		Pixmap[] maps = new Pixmap[mipMapLevels * 6];
		int index = 0;
		for(int level=0 ; level<mipMapLevels ; level++){
			int size = 1 << (mipMapLevels - level - 1);
			FrameBuffer fbo = new FrameBuffer(Format.RGBA8888, size, size, false);
			fbo.begin();
			for(int s=0 ; s<6 ; s++){
				Gdx.gl.glClearColor(0, 0, 0, 0);
				Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
				
				CubemapSide side = CubemapSide.values()[s];
				
				float blur = (float)level / (float)mipMapLevels;
				
				renderGradient(side, blur);
				renderLights(side, false);
				
				maps[index] = Pixmap.createFromFrameBuffer(0, 0, size, size);
				index++;
			}
			fbo.end();
			fbo.dispose();
		}
		FacedMultiCubemapData data = new FacedMultiCubemapData(maps, mipMapLevels);
		Cubemap map = new Cubemap(data);
		map.setFilter(TextureFilter.MipMap, TextureFilter.Linear);
		return map;
	}
	
	private void renderGradient(CubemapSide side, float blur){
		if(!renderGradient) return;
		
		Color aveSky = farSkyColor.cpy().lerp(nearSkyColor, .5f);
		Color aveGnd = farGroundColor.cpy().lerp(nearGroundColor, .5f);
		
		Color ave = aveSky.cpy().lerp(aveGnd, .5f);
		
		Color aveHorizon = farGroundColor.cpy().lerp(farSkyColor, .5f);

		// blur!
		float t2 = 1 - (float)Math.pow(1 - blur, 4);
		float t = 1 - (float)Math.pow(1 - blur, 1);

		Color ngc = nearGroundColor.cpy().lerp(ave, t);
		Color nsc = nearSkyColor.cpy().lerp(ave, t);
		
		Color fgc = farGroundColor.cpy().lerp(aveHorizon, t2).lerp(ave, t);
		Color fsc = farSkyColor.cpy().lerp(aveHorizon, t2).lerp(ave, t);

		shapes.begin(ShapeType.Filled);
		if(side == CubemapSide.PositiveY){
			shapes.rect(0, 0, 1, 1, nsc, nsc, nsc, nsc);
		}
		else if(side == CubemapSide.NegativeY){
			shapes.rect(0, 0, 1, 1, ngc, ngc, ngc, ngc);
		}
		else{
			// draw vertical gradient
			shapes.rect(0, 0, 1, .5f, nsc, nsc, fsc, fsc);
			shapes.rect(0, .5f, 1, .5f, fgc, fgc, ngc, ngc);
		}
		shapes.end();
	}
	
	public static class Light {
		
		public final Color color = new Color(1f, 1f, 1f, 1f);
		public final Vector3 direction = new Vector3(0, -1, 0);
		public float exponent = 30f;
		
		private static final Vector3 localSunDir = new Vector3();
		private static final Vector3 localDir = new Vector3();
		private static final Vector3 localUp = new Vector3();
		private static final Matrix4 matrix = new Matrix4();
		
		private void render(CubemapSide side, ShapeRenderer shapes, ShaderProgram shader, float strength){
			render(side, shapes, shader, strength, exponent);
		}
		private void render(CubemapSide side, ShapeRenderer shapes, ShaderProgram shader, float strength, float exponent){
			shader.bind();
			shader.setUniformf("u_exponent", exponent);
			shader.setUniformf("u_ambient", color.r, color.g, color.b, 0f);
			shader.setUniformf("u_diffuse", color.r, color.g, color.b, strength);

			localDir.set(side.direction);
			localUp.set(side.up);
			
			// XXX patch
			if(side == CubemapSide.NegativeX || side == CubemapSide.PositiveX){
				localDir.x = -localDir.x;
			}
				
			matrix.setToLookAt(localDir, localUp).tra();
			localSunDir.set(direction).scl(-1, -1, 1).mul(matrix); // XXX patch again
			
			shader.setUniformf("u_direction", localSunDir);
			
			shapes.begin(ShapeType.Filled);
			shapes.rect(0, 0, 1, 1);
			shapes.end();
		}
	}

	private void renderLights(CubemapSide side, boolean blured){

		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
		
		for(Light light : lights){
			if(blured){
				light.render(side, sunShapes, sunShader, .5f, 1f);
			}else{
				light.render(side, sunShapes, sunShader, 1f);
			}
		}
		
		Gdx.gl.glDisable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
	}
	
}
