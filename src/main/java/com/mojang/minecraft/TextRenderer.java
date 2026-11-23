package com.mojang.minecraft;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.util.glu.GLU.gluBuild2DMipmaps;

public class TextRenderer {
    
    private static final int FONT_SIZE = 16;
    private java.awt.Font font;
    private int fontTextureId;
    private static final int CHARS_PER_ROW = 16;
    private static final int CHAR_SIZE = 16;
    
    public TextRenderer() {
        try {
            // Load the OTF font
            InputStream fontStream = TextRenderer.class.getResourceAsStream("/Minecraft.otf");
            if (fontStream == null) {
                System.out.println("Font file not found, using fallback");
                font = new java.awt.Font("Arial", java.awt.Font.PLAIN, FONT_SIZE);
            } else {
                font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontStream).deriveFont((float) FONT_SIZE);
                fontStream.close();
                System.out.println("Font loaded successfully: " + font.getFontName());
            }
            
            // Create font texture atlas
            createFontAtlas();
        } catch (Exception e) {
            System.out.println("Error loading font, using fallback: " + e.getMessage());
            e.printStackTrace();
            // Fallback to default font
            font = new java.awt.Font("Arial", java.awt.Font.PLAIN, FONT_SIZE);
        }
    }
    
    private void createFontAtlas() {
        int atlasSize = CHAR_SIZE * CHARS_PER_ROW;
        BufferedImage atlas = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        
        // Draw all characters in a grid
        for (int i = 0; i < 256; i++) {
            char c = (char) i;
            int row = i / CHARS_PER_ROW;
            int col = i % CHARS_PER_ROW;
            int x = col * CHAR_SIZE;
            int y = row * CHAR_SIZE;
            g.drawString(String.valueOf(c), x + 2, y + CHAR_SIZE - 2);
        }
        g.dispose();
        
        System.out.println("Font atlas created, size: " + atlasSize + "x" + atlasSize);
        
        // Create texture from atlas
        int width = atlas.getWidth();
        int height = atlas.getHeight();
        
        fontTextureId = glGenTextures();
        System.out.println("Generated texture ID: " + fontTextureId);
        glBindTexture(GL_TEXTURE_2D, fontTextureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        
        int[] pixels = new int[width * height];
        atlas.getRGB(0, 0, width, height, pixels, 0, width);
        
        for (int i = 0; i < pixels.length; i++) {
            int alpha = pixels[i] >> 24 & 0xFF;
            int red = pixels[i] >> 16 & 0xFF;
            int green = pixels[i] >> 8 & 0xFF;
            int blue = pixels[i] & 0xFF;
            pixels[i] = alpha << 24 | blue << 16 | green << 8 | red;
        }
        
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(width * height * 4);
        byteBuffer.asIntBuffer().put(pixels);
        
        gluBuild2DMipmaps(GL_TEXTURE_2D, GL_RGBA, width, height, GL_RGBA, GL_UNSIGNED_BYTE, byteBuffer);
        System.out.println("Font atlas texture uploaded to GPU");
    }
    
    /**
     * Render text at the given position
     * 
     * @param text Text to render
     * @param x X position (screen coordinates)
     * @param y Y position (screen coordinates)
     * @param scale Scale of the text
     */
    public void render(String text, float x, float y, float scale) {
        System.out.println("Rendering: '" + text + "' at (" + x + ", " + y + ") scale: " + scale + " textureId: " + fontTextureId);
        
        glDisable(GL_DEPTH_TEST);
        
        // First, test with a simple red quad without texture
        glDisable(GL_TEXTURE_2D);
        glPushMatrix();
        glTranslatef(x, y, 0);
        glColor4f(1.0f, 0.0f, 0.0f, 1.0f); // Red
        
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(100, 0);
        glVertex2f(100, 20);
        glVertex2f(0, 20);
        glEnd();
        
        glPopMatrix();
        
        // Now try with texture
        glEnable(GL_TEXTURE_2D);
        Textures.bind(fontTextureId);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glPushMatrix();
        glTranslatef(x, y, 0);
        glScalef(scale, scale, 1.0f);
        
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        
        float currentX = 0;
        int atlasSize = CHAR_SIZE * CHARS_PER_ROW;
        
        glBegin(GL_QUADS);
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charIndex = (int) c;
            
            int row = charIndex / CHARS_PER_ROW;
            int col = charIndex % CHARS_PER_ROW;
            
            float uMin = (col * CHAR_SIZE) / (float) atlasSize;
            float uMax = ((col + 1) * CHAR_SIZE) / (float) atlasSize;
            float vMin = (row * CHAR_SIZE) / (float) atlasSize;
            float vMax = ((row + 1) * CHAR_SIZE) / (float) atlasSize;
            
            glTexCoord2f(uMin, vMin);
            glVertex2f(currentX, 0);
            
            glTexCoord2f(uMax, vMin);
            glVertex2f(currentX + CHAR_SIZE, 0);
            
            glTexCoord2f(uMax, vMax);
            glVertex2f(currentX + CHAR_SIZE, CHAR_SIZE);
            
            glTexCoord2f(uMin, vMax);
            glVertex2f(currentX, CHAR_SIZE);
            
            currentX += CHAR_SIZE;
        }
        
        glEnd();
        
        glPopMatrix();
        
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        
        System.out.println("Render complete");
    }
}
