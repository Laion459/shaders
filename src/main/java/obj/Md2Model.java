package obj;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

import org.lwjgl.BufferUtils;

import Model.Model;

/**
 * Carrega e renderiza modelos MD2 (Quake II).
 * Mantém um VBO de posição por frame; a interpolação é feita no vertex shader.
 */
public class Md2Model extends Model {

	private static final int MD2_IDENT = (('2' << 24) + ('P' << 16) + ('D' << 8) + 'I');
	private static final int MD2_VERSION = 8;

	public int skinWidth;
	public int skinHeight;
	public int numFrames;
	public int numTriangles;
	public int numVertices;
	public int nvertex;

	public String[] frameNames;
	public float[][] framePositions; // [frame][expanded xyz...]
	public float[] texCoords; // expanded uv

	private int[] vboPositionHandles;
	private int vboTexCoordHandle;
	private int vboNormalHandle;
	private int vboColorHandle;

	private int currentFrame = 0;
	private int nextFrame = 1;

	public void loadMd2(String file) {
		try (FileInputStream fis = new FileInputStream(file);
				FileChannel channel = fis.getChannel()) {

			ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
			channel.read(buffer);
			buffer.flip();
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			int ident = buffer.getInt();
			int version = buffer.getInt();
			if (ident != MD2_IDENT || version != MD2_VERSION) {
				throw new IOException("Arquivo MD2 invalido: " + file);
			}

			skinWidth = buffer.getInt();
			skinHeight = buffer.getInt();
			int frameSize = buffer.getInt();
			int numSkins = buffer.getInt();
			numVertices = buffer.getInt();
			int numTexCoords = buffer.getInt();
			numTriangles = buffer.getInt();
			int numGlCommands = buffer.getInt();
			numFrames = buffer.getInt();

			int offsetSkins = buffer.getInt();
			int offsetTexCoords = buffer.getInt();
			int offsetTriangles = buffer.getInt();
			int offsetFrames = buffer.getInt();
			int offsetGlCommands = buffer.getInt();
			int offsetEnd = buffer.getInt();

			short[][] st = new short[numTexCoords][2];
			buffer.position(offsetTexCoords);
			for (int i = 0; i < numTexCoords; i++) {
				st[i][0] = buffer.getShort();
				st[i][1] = buffer.getShort();
			}

			int[][] triangles = new int[numTriangles][6];
			buffer.position(offsetTriangles);
			for (int i = 0; i < numTriangles; i++) {
				triangles[i][0] = buffer.getShort() & 0xFFFF; // vertex index 0
				triangles[i][1] = buffer.getShort() & 0xFFFF;
				triangles[i][2] = buffer.getShort() & 0xFFFF;
				triangles[i][3] = buffer.getShort() & 0xFFFF; // st index 0
				triangles[i][4] = buffer.getShort() & 0xFFFF;
				triangles[i][5] = buffer.getShort() & 0xFFFF;
			}

			nvertex = numTriangles * 3;
			frameNames = new String[numFrames];
			framePositions = new float[numFrames][nvertex * 3];
			texCoords = new float[nvertex * 2];

			float[][] rawFrames = new float[numFrames][numVertices * 3];

			buffer.position(offsetFrames);
			for (int f = 0; f < numFrames; f++) {
				float scaleX = buffer.getFloat();
				float scaleY = buffer.getFloat();
				float scaleZ = buffer.getFloat();
				float transX = buffer.getFloat();
				float transY = buffer.getFloat();
				float transZ = buffer.getFloat();

				byte[] nameBytes = new byte[16];
				buffer.get(nameBytes);
				int nameLen = 0;
				while (nameLen < 16 && nameBytes[nameLen] != 0) {
					nameLen++;
				}
				frameNames[f] = new String(nameBytes, 0, nameLen);

				for (int v = 0; v < numVertices; v++) {
					int x = buffer.get() & 0xFF;
					int y = buffer.get() & 0xFF;
					int z = buffer.get() & 0xFF;
					buffer.get(); // normal index

					// MD2: X right, Z up, Y into screen -> OpenGL: X right, Y up, -Z forward
					rawFrames[f][v * 3] = x * scaleX + transX;
					rawFrames[f][v * 3 + 1] = z * scaleZ + transZ;
					rawFrames[f][v * 3 + 2] = -(y * scaleY + transY);
				}

				// frameSize pode incluir padding; reposiciona se necessario
				int expected = offsetFrames + (f + 1) * frameSize;
				if (buffer.position() < expected && f < numFrames - 1) {
					buffer.position(expected);
				}
			}

			for (int t = 0; t < numTriangles; t++) {
				for (int k = 0; k < 3; k++) {
					int vertIndex = triangles[t][k];
					int stIndex = triangles[t][3 + k];
					int outVert = (t * 3 + k);

					for (int f = 0; f < numFrames; f++) {
						framePositions[f][outVert * 3] = rawFrames[f][vertIndex * 3];
						framePositions[f][outVert * 3 + 1] = rawFrames[f][vertIndex * 3 + 1];
						framePositions[f][outVert * 3 + 2] = rawFrames[f][vertIndex * 3 + 2];
					}

					texCoords[outVert * 2] = (st[stIndex][0] / (float) skinWidth);
					texCoords[outVert * 2 + 1] = (st[stIndex][1] / (float) skinHeight);
				}
			}

			System.out.println("MD2 carregado: " + file);
			System.out.println(" frames=" + numFrames + " tris=" + numTriangles + " verts=" + numVertices
					+ " skin=" + skinWidth + "x" + skinHeight);
			System.out.println(" primeiro frame: " + frameNames[0] + " ultimo: " + frameNames[numFrames - 1]);

			// evita warnings de campos nao usados do header
			if (numSkins < 0 || numGlCommands < 0 || offsetSkins < 0 || offsetGlCommands < 0 || offsetEnd < 0) {
				throw new IOException("Header MD2 inconsistente");
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void load() {
		if (framePositions == null) {
			return;
		}

		vboPositionHandles = new int[numFrames];
		for (int f = 0; f < numFrames; f++) {
			FloatBuffer posData = BufferUtils.createFloatBuffer(framePositions[f].length);
			posData.put(framePositions[f]);
			posData.flip();

			vboPositionHandles[f] = glGenBuffers();
			glBindBuffer(GL_ARRAY_BUFFER, vboPositionHandles[f]);
			glBufferData(GL_ARRAY_BUFFER, posData, GL_STATIC_DRAW);
			glBindBuffer(GL_ARRAY_BUFFER, 0);
		}

		FloatBuffer uvData = BufferUtils.createFloatBuffer(texCoords.length);
		uvData.put(texCoords);
		uvData.flip();
		vboTexCoordHandle = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboTexCoordHandle);
		glBufferData(GL_ARRAY_BUFFER, uvData, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);

		// normais/cores dummy no padrao do professor (attrs 1 e 2)
		float[] normals = new float[nvertex * 3];
		float[] colors = new float[nvertex * 3];
		for (int i = 0; i < nvertex; i++) {
			normals[i * 3 + 1] = 1.0f;
			colors[i * 3] = 1.0f;
			colors[i * 3 + 1] = 1.0f;
			colors[i * 3 + 2] = 1.0f;
		}

		FloatBuffer normalData = BufferUtils.createFloatBuffer(normals.length);
		normalData.put(normals);
		normalData.flip();
		vboNormalHandle = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboNormalHandle);
		glBufferData(GL_ARRAY_BUFFER, normalData, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);

		FloatBuffer colorData = BufferUtils.createFloatBuffer(colors.length);
		colorData.put(colors);
		colorData.flip();
		vboColorHandle = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboColorHandle);
		glBufferData(GL_ARRAY_BUFFER, colorData, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void setFrames(int frameA, int frameB) {
		if (numFrames <= 0) {
			return;
		}
		currentFrame = ((frameA % numFrames) + numFrames) % numFrames;
		nextFrame = ((frameB % numFrames) + numFrames) % numFrames;
	}

	public int getNumFrames() {
		return numFrames;
	}

	@Override
	public void draw() {
		if (vboPositionHandles == null) {
			return;
		}

		glBindBuffer(GL_ARRAY_BUFFER, vboColorHandle);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);

		glBindBuffer(GL_ARRAY_BUFFER, vboNormalHandle);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, 0, 0);

		glBindBuffer(GL_ARRAY_BUFFER, vboTexCoordHandle);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, 0, 0);

		glBindBuffer(GL_ARRAY_BUFFER, vboPositionHandles[currentFrame]);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

		glBindBuffer(GL_ARRAY_BUFFER, vboPositionHandles[nextFrame]);
		glEnableVertexAttribArray(4);
		glVertexAttribPointer(4, 3, GL_FLOAT, false, 0, 0);

		glDrawArrays(GL_TRIANGLES, 0, nvertex);

		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}
}
