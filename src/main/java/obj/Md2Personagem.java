package obj;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

import java.nio.FloatBuffer;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import shaders.ShaderProgram;

/**
 * Entidade animada MD2 no mesmo padrao do Cubo3D.
 * Avanca frames na CPU e manda o fator de interpolacao para o vertex shader.
 */
public class Md2Personagem extends Object3D {

	public Md2Model model = null;
	public int textureId = 0;
	public float rotvel = 0;
	public float animSpeed = 8.0f; // frames por segundo

	FloatBuffer matrixBuffer = MemoryUtil.memAllocFloat(16);

	float ang = 0;
	int frameAtual = 0;
	float interpolacao = 0.0f;

	int animInicio = 0;
	int animFim = 0;

	public Md2Personagem(float x, float y, float z, float r) {
		super(x, y, z);
		raio = r;
	}

	public void setAnimacao(int inicio, int fim) {
		animInicio = inicio;
		animFim = fim;
		frameAtual = inicio;
		interpolacao = 0.0f;
	}

	@Override
	public void DesenhaSe(ShaderProgram shader) {
		if (model == null) {
			return;
		}

		if (textureId != 0) {
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GL_TEXTURE_2D, textureId);
			int loctexture = glGetUniformLocation(shader.programID, "tex");
			glUniform1i(loctexture, 0);
		}

		Matrix4f modelm = new Matrix4f();
		modelm.setIdentity();
		modelm.translate(new Vector3f(x, y, z));
		modelm.scale(new Vector3f(raio, raio, raio));
		modelm.rotate(ang, new Vector3f(0.0f, 1.0f, 0.0f));

		int modellocation = glGetUniformLocation(shader.programID, "model");
		modelm.storeTranspose(matrixBuffer);
		matrixBuffer.flip();
		glUniformMatrix4fv(modellocation, false, matrixBuffer);

		int interpLoc = glGetUniformLocation(shader.programID, "interpolacao");
		glUniform1f(interpLoc, interpolacao);

		int proximo = frameAtual + 1;
		if (proximo > animFim) {
			proximo = animInicio;
		}
		model.setFrames(frameAtual, proximo);
		model.draw();
	}

	@Override
	public void SimulaSe(long diftime) {
		super.SimulaSe(diftime);

		x += vx * diftime / 1000.0f;
		y += vy * diftime / 1000.0f;
		z += vz * diftime / 1000.0f;
		ang += rotvel * diftime / 1000.0f;

		if (model == null || model.getNumFrames() <= 0) {
			return;
		}

		if (animFim <= animInicio) {
			animInicio = 0;
			animFim = model.getNumFrames() - 1;
		}

		interpolacao += animSpeed * diftime / 1000.0f;
		while (interpolacao >= 1.0f) {
			interpolacao -= 1.0f;
			frameAtual++;
			if (frameAtual > animFim) {
				frameAtual = animInicio;
			}
		}
	}
}
