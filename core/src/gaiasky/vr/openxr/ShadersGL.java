package gaiasky.vr.openxr;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class ShadersGL {
    public static final String texVertShader = "#version 330 core\n" +
            "layout (location = 0) in vec3 aPos;\n" +
            "layout (location = 1) in vec2 aTexCoord;\n" +
            "\n" +
            "out vec2 TexCoord;\n" +
            "\n" +
            "uniform mat4 model;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 projection;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
            "    TexCoord = vec2(aTexCoord.x, aTexCoord.y);\n" +
            "}";

    public static final String screenVertShader = "#version 330 core\n" +
            "layout (location = 0) in vec2 aPos;\n" +
            "layout (location = 1) in vec2 aTexCoords;\n" +
            "\n" +
            "out vec2 TexCoords;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    TexCoords = aTexCoords;\n" +
            "    gl_Position = vec4(aPos.x, aPos.y, 0.0, 1.0); \n" +
            "}";

    public static final String texFragShader = "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "\n" +
            "in vec2 TexCoords;\n" +
            "\n" +
            "uniform sampler2D textureSampler;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    vec3 col = texture(textureSampler, TexCoords).rgb;\n" +
            "    FragColor = vec4(col, 1.0);\n" +
            "}";

    public static final String colVertShader = "#version 330 core\n" +
            "layout (location = 0) in vec3 aPos;\n" +
            "layout (location = 1) in vec3 aColorIn;\n" +
            "\n" +
            "out vec3 Color;\n" +
            "\n" +
            "uniform mat4 model;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 projection;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    //gl_Position = transform * vec4(aPos, 1.0f);\n" +
            "    gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
            "    Color = aColorIn;\n" +
            "}";

    public static final String colFragShader = "#version 330 core\n" +
            "in vec3 Color;" +
            "out vec4 FragColor;\n" +
            "void main()\n" +
            "{\n" +
            "   FragColor = vec4(Color, 1.0f);\n" +
            "}";

    public static int createShaderProgram(String vertexShaderGlsl, String fragmentShaderGlsl) {
        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexShaderGlsl);
        GL20.glCompileShader(vertexShader);
        checkShader(vertexShader);

        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentShaderGlsl);
        GL20.glCompileShader(fragmentShader);
        checkShader(fragmentShader);

        int program = glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        checkProgram(program);

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        return program;
    }

    public static void checkShader(int shader) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer r = stack.mallocInt(1);
            GL20.glGetShaderiv(shader, GL_COMPILE_STATUS, r);
            if (r.get(0) == GL11.GL_FALSE) {
                String msg = GL20.glGetShaderInfoLog(shader, 4096);
                throw new IllegalStateException("Compile shader failed: " + msg);
            }
        }
    }

    public static void checkProgram(int program) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer r = stack.mallocInt(1);
            GL20.glGetProgramiv(program, GL_LINK_STATUS, r);
            if (r.get(0) == GL11.GL_FALSE) {
                String msg = GL20.glGetProgramInfoLog(program, 4096);
                throw new IllegalStateException("Link program failed: " + msg);
            }
        }
    }
}
