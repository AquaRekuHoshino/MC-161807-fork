package com.mojang.minecraft;

import com.mojang.minecraft.character.Zombie;
import com.mojang.minecraft.level.*;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.particle.ParticleEngine;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.util.glu.GLU.gluPerspective;
import static org.lwjgl.util.glu.GLU.gluPickMatrix;

public class Minecraft implements Runnable {

    private final Timer timer = new Timer(20);

    private Level level;
    private LevelRenderer levelRenderer;
    private Player player;

    private final List<Zombie> zombies = new ArrayList<>();
    private ParticleEngine particleEngine;

    /**
     * Fog
     */
    private final FloatBuffer fogColorDaylight = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer fogColorShadow = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(16);

    /**
     * Tile picking
     */
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private final IntBuffer selectBuffer = BufferUtils.createIntBuffer(2000);
    private HitResult hitResult;

    /**
     * HUD rendering
     */
    private final Tessellator tessellator = new Tessellator();

    /**
     * Selected tile in hand
     */
    private int selectedTileId = 1;

    /**
     * Canvas
     */
    private final Canvas parent;
    private int width;
    private int height;
    private final boolean fullscreen;
    public boolean appletMode;

    /**
     * Game state
     */
    public volatile boolean running;

    /**
     * Corruption state
     */
    public static boolean corruptedMode = false;

    /**
     * Text renderer
     */
    private TextRenderer textRenderer;

    /**
     * Game version
     */
    private static final String VERSION = "v1.0.0";

    /**
     * Create Minecraft instance and render it on a canvas
     *
     * @param parent     The canvas as render target
     * @param width      Canvas width
     * @param height     Canvas height
     * @param fullscreen Is in fullscreen
     */
    public Minecraft(Canvas parent, int width, int height, boolean fullscreen) {
        this.parent = parent;
        this.width = width;
        this.height = height;
        this.fullscreen = fullscreen;
    }

    /**
     * Extract native libraries from JAR to temp directory
     */
    private static void extractNatives() {
        try {
            Path tempDir = Files.createTempDirectory("lwjgl_natives");
            tempDir.toFile().deleteOnExit();

            String os = System.getProperty("os.name").toLowerCase();
            String extension;
            if (os.contains("win")) {
                extension = ".dll";
            } else if (os.contains("mac")) {
                extension = ".dylib";
            } else {
                extension = ".so";
            }

            // List all files in the natives directory from the JAR
            java.security.ProtectionDomain codeSource = Minecraft.class.getProtectionDomain();
            if (codeSource != null && codeSource.getCodeSource() != null && codeSource.getCodeSource().getLocation() != null) {
                java.util.jar.JarFile jarFile = new java.util.jar.JarFile(codeSource.getCodeSource().getLocation().getPath());
                java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("natives/") && name.endsWith(extension)) {
                        InputStream is = jarFile.getInputStream(entry);
                        Path targetPath = tempDir.resolve(name.substring(name.lastIndexOf('/') + 1));
                        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        targetPath.toFile().deleteOnExit();
                        is.close();
                    }
                }
                jarFile.close();
            }

            System.setProperty("org.lwjgl.librarypath", tempDir.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize the game.
     * Setup display, keyboard, mouse, rendering and camera
     *
     * @throws LWJGLException Game could not be initialized
     */
    public void init() throws LWJGLException {
        // Check for corruption file
        checkCorruptionFile();

        // Write fog color for daylight
        if (corruptedMode) {
            this.fogColorDaylight.put(new float[]{
                    100 / 255.0F,
                    0 / 255.0F,
                    0 / 255.0F,
                    255 / 255.0F
            }).flip();
        } else {
            this.fogColorDaylight.put(new float[]{
                    254 / 255.0F,
                    251 / 255.0F,
                    250 / 255.0F,
                    255 / 255.0F
            }).flip();
        }

        // Write fog color for shadow
        if (corruptedMode) {
            this.fogColorShadow.put(new float[]{
                    30 / 255.0F,
                    0 / 255.0F,
                    0 / 255.0F,
                    255 / 255.0F
            }).flip();
        } else {
            this.fogColorShadow.put(new float[]{
                    14 / 255.0F,
                    11 / 255.0F,
                    10 / 255.0F,
                    255 / 255.0F
            }).flip();
        }

        if (this.parent == null) {
            if (this.fullscreen) {
                // Set in fullscreen
                Display.setFullscreen(true);

                // Set monitor size
                this.width = Display.getDisplayMode().getWidth();
                this.height = Display.getDisplayMode().getHeight();
            } else {
                // Set defined window size
                Display.setDisplayMode(new DisplayMode(this.width, this.height));
            }
        } else {
            // Set canvas parent
            Display.setParent(this.parent);
        }

        // Setup I/O
        Display.create();
        Keyboard.create();
        Mouse.create();

        // Setup texture and color
        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        
        // Set background color based on corruption mode
        if (corruptedMode) {
            glClearColor(0.2F, 0.0F, 0.0F, 1.0F); // Dark red background
        } else {
            glClearColor(0.5F, 0.8F, 1.0F, 0.0F); // Normal blue sky
        }
        
        glClearDepth(1.0);

        // Setup depth
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LEQUAL);

        // Setup alpha
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.5F);

        // Setup camera
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);

        // Create level and player (Has to be in main thread)
        this.level = new Level(1024, 1024, 256);
        this.levelRenderer = new LevelRenderer(this.level);
        this.player = new Player(this.level);
        this.level.setPlayer(this.player);
        this.particleEngine = new ParticleEngine(this.level);
        this.textRenderer = new TextRenderer();

        // Grab mouse cursor
        Mouse.setGrabbed(true);

        // Spawn some zombies
        for (int i = 0; i < 10; ++i) {
            Zombie zombie = new Zombie(this.level, 128.0F, 0.0F, 129.0F);
            zombie.resetPosition();
            zombie.setPlayer(this.player);
            this.zombies.add(zombie);
        }
    }

    /**
     * Destroy mouse, keyboard and display
     */
    public void destroy() {
        this.level.save();

        Mouse.destroy();
        Keyboard.destroy();
        Display.destroy();
    }

    /**
     * Main game thread
     * Responsible for the game loop
     */
    @Override
    public void run() {
        // Game is running
        this.running = true;

        try {
            // Initialize the game
            init();
        } catch (Exception e) {
            // Show error message dialog and stop the game
            JOptionPane.showMessageDialog(null, e, "Failed to start Minecraft", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        // To keep track of framerate
        int frames = 0;
        long lastTime = System.currentTimeMillis();

        try {
            // Start the game loop
            while (this.running) {
                // On close window
                if (this.parent == null && Display.isCloseRequested()) {
                    this.stop();
                }

                // Update the timer
                this.timer.advanceTime();

                // Call the tick to reach updates 20 per seconds
                for (int i = 0; i < this.timer.ticks; ++i) {
                    onTick();
                }

                // Render the game
                render(this.timer.partialTicks);

                // Increase rendered frame
                frames++;

                // Loop if a second passed
                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    // Print amount of frames
                    System.out.println(frames + " fps, " + Chunk.updates);

                    // Reset global rebuild stats
                    Chunk.updates = 0;

                    // Increase last time printed and reset frame counter
                    lastTime += 1000L;
                    frames = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Destroy I/O and save game
            destroy();
        }
    }

    /**
     * Stop the game
     */
    public void stop() {
        this.running = false;
    }

    /**
     * Game tick, called exactly 20 times per second
     */
    private void onTick() {
        // Handle corruption timer
        if (com.mojang.minecraft.level.tile.CorruptTile.corruptionTriggered) {
            com.mojang.minecraft.level.tile.CorruptTile.corruptionTimer--;
            
            if (com.mojang.minecraft.level.tile.CorruptTile.corruptionTimer <= 0) {
                // Timer finished - execute corruption finale
                executeCorruptionFinale();
            }
        }

        // Listen for keyboard inputs
        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {

                // Exit the game
                if (Keyboard.getEventKey() == 1) { // Escape
                    stop();
                }

                // Save the level
                if (Keyboard.getEventKey() == 28) { // Enter
                    this.level.save();
                }

                // Tile selection
                if (Keyboard.getEventKey() == 2) { // 1
                    this.selectedTileId = Tile.rock.id;
                }
                if (Keyboard.getEventKey() == 3) { // 2
                    this.selectedTileId = Tile.dirt.id;
                }
                if (Keyboard.getEventKey() == 4) { // 3
                    this.selectedTileId = Tile.stoneBrick.id;
                }
                if (Keyboard.getEventKey() == 5) { // 4
                    this.selectedTileId = Tile.wood.id;
                }
                if (Keyboard.getEventKey() == 7) { // 6
                    this.selectedTileId = Tile.bush.id;
                }
                if (Keyboard.getEventKey() == 10) { // 9
                    this.selectedTileId = Tile.corrupt.id;
                }

                // Spawn zombie
                if (Keyboard.getEventKey() == 34) { // G
                    Zombie zombie = new Zombie(this.level, this.player.x, this.player.y, this.player.z);
                    zombie.setPlayer(this.player);
                    this.zombies.add(zombie);
                }
            }
        }

        // Tick random tile in level
        this.level.onTick();

        // Tick particles
        this.particleEngine.onTick();

        // Tick zombies
        Iterator<Zombie> iterator = this.zombies.iterator();
        while (iterator.hasNext()) {
            Zombie zombie = iterator.next();

            // Tick zombie
            zombie.onTick();

            // Remove zombie
            if (zombie.removed) {
                iterator.remove();
            }
        }

        // Tick player
        this.player.onTick();
    }

    /**
     * Move and rotate the camera to players location and rotation
     *
     * @param partialTicks Overflow ticks to interpolate
     */
    private void moveCameraToPlayer(float partialTicks) {
        Entity player = this.player;

        // Eye height
        glTranslatef(0.0f, 0.0f, -0.3f);

        // Rotate camera
        glRotatef(player.xRotation, 1.0f, 0.0f, 0.0f);
        glRotatef(player.yRotation, 0.0f, 1.0f, 0.0f);

        // Smooth movement
        double x = this.player.prevX + (this.player.x - this.player.prevX) * partialTicks;
        double y = this.player.prevY + (this.player.y - this.player.prevY) * partialTicks;
        double z = this.player.prevZ + (this.player.z - this.player.prevZ) * partialTicks;

        // Move camera to players location
        glTranslated(-x, -y, -z);
    }


    /**
     * Setup the normal player camera
     *
     * @param partialTicks Overflow ticks to interpolate
     */
    private void setupCamera(float partialTicks) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        // Set camera perspective
        gluPerspective(70, width / (float) height, 0.05F, 1000F);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Move camera to middle of level
        moveCameraToPlayer(partialTicks);
    }

    /**
     * Setup tile picking camera
     *
     * @param partialTicks Overflow ticks to calculate smooth a movement
     * @param x            Screen position x
     * @param y            Screen position y
     */
    private void setupPickCamera(float partialTicks, int x, int y) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        // Reset buffer
        this.viewportBuffer.clear();

        // Get viewport value
        glGetInteger(GL_VIEWPORT, this.viewportBuffer);

        // Flip
        this.viewportBuffer.flip();
        this.viewportBuffer.limit(16);

        // Set matrix and camera perspective
        gluPickMatrix(x, y, 5.0F, 5.0F, this.viewportBuffer);
        gluPerspective(70.0F, this.width / (float) this.height, 0.05F, 1000.0F);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Move camera to middle of level
        moveCameraToPlayer(partialTicks);
    }

    /**
     * @param partialTicks Overflow ticks to interpolate
     */
    private void pick(float partialTicks) {
        // Reset select buffer
        this.selectBuffer.clear();

        glSelectBuffer(this.selectBuffer);
        glRenderMode(GL_SELECT);

        // Setup pick camera
        this.setupPickCamera(partialTicks, this.width / 2, this.height / 2);

        // Render all possible pick selection faces to the target
        this.levelRenderer.pick(this.player, Frustum.getFrustum());

        // Flip buffer
        this.selectBuffer.flip();
        this.selectBuffer.limit(this.selectBuffer.capacity());

        long closest = 0L;
        int[] names = new int[10];
        int hitNameCount = 0;

        // Get amount of hits
        int hits = glRenderMode(GL_RENDER);
        for (int hitIndex = 0; hitIndex < hits; hitIndex++) {

            // Get name count
            int nameCount = this.selectBuffer.get();
            long minZ = this.selectBuffer.get();
            this.selectBuffer.get();

            // Check if the hit is closer to the camera
            if (minZ < closest || hitIndex == 0) {
                closest = minZ;
                hitNameCount = nameCount;

                // Fill names
                for (int nameIndex = 0; nameIndex < nameCount; nameIndex++) {
                    names[nameIndex] = this.selectBuffer.get();
                }
            } else {
                // Skip names
                for (int nameIndex = 0; nameIndex < nameCount; ++nameIndex) {
                    this.selectBuffer.get();
                }
            }
        }

        // Update hit result
        if (hitNameCount > 0) {
            this.hitResult = new HitResult(names[0], names[1], names[2], names[3], names[4]);
        } else {
            this.hitResult = null;
        }
    }


    /**
     * Rendering the game
     *
     * @param partialTicks Overflow ticks to interpolate
     */
    private void render(float partialTicks) {
        // Get mouse motion
        float motionX = Mouse.getDX();
        float motionY = Mouse.getDY();

        // Rotate the camera using the mouse motion input
        this.player.turn(motionX, motionY);

        // Pick tile
        pick(partialTicks);

        // Listen for mouse inputs
        while (Mouse.next()) {
            // Right click
            if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState() && this.hitResult != null) {
                Tile previousTile = Tile.tiles[this.level.getTile(this.hitResult.x, this.hitResult.y, this.hitResult.z)];

                // Destroy the tile
                boolean tileChanged = this.level.setTile(this.hitResult.x, this.hitResult.y, this.hitResult.z, 0);

                // Create particles for this tile
                if (previousTile != null && tileChanged) {
                    previousTile.onDestroy(this.level, this.hitResult.x, this.hitResult.y, this.hitResult.z, this.particleEngine);
                }
            }

            // Left click
            if (Mouse.getEventButton() == 1 && Mouse.getEventButtonState() && this.hitResult != null) {
                // Get target tile position
                int x = this.hitResult.x;
                int y = this.hitResult.y;
                int z = this.hitResult.z;

                // Get position of the tile using face direction
                if (this.hitResult.face == 0) y--;
                if (this.hitResult.face == 1) y++;
                if (this.hitResult.face == 2) z--;
                if (this.hitResult.face == 3) z++;
                if (this.hitResult.face == 4) x--;
                if (this.hitResult.face == 5) x++;

                // Set the tile
                this.level.setTile(x, y, z, this.selectedTileId);
            }
        }

        // Clear color and depth buffer and reset the camera
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Setup normal player camera
        setupCamera(partialTicks);
        glEnable(GL_CULL_FACE);

        // Get current frustum
        Frustum frustum = Frustum.getFrustum();

        // Update dirty chunks
        this.levelRenderer.updateDirtyChunks(this.player);

        // Setup daylight fog
        setupFog(0);
        glEnable(GL_FOG);

        // Render bright tiles
        this.levelRenderer.render(0);

        // Render zombies in sunlight
        for (Zombie zombie : this.zombies) {
            if (zombie.isLit() && frustum.isVisible(zombie.boundingBox)) {
                zombie.render(partialTicks);
            }
        }

        // Render particles in sunlight
        this.particleEngine.render(this.player, this.tessellator, partialTicks, 0);

        // Setup shadow fog
        setupFog(1);

        // Render dark tiles in shadow
        this.levelRenderer.render(1);

        // Render zombies in shadow
        for (Zombie zombie : this.zombies) {
            if (!zombie.isLit() && frustum.isVisible(zombie.boundingBox)) {
                zombie.render(partialTicks);
            }
        }

        // Render particles in shadow
        this.particleEngine.render(this.player, this.tessellator, partialTicks, 1);

        // Finish rendering
        glDisable(GL_LIGHTING);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);

        // Render the actual hit
        if (this.hitResult != null) {
            glDisable(GL_ALPHA_TEST);
            this.levelRenderer.renderHit(this.hitResult);
            glEnable(GL_ALPHA_TEST);
        }

        // Draw player HUD
        drawGui(partialTicks);

        // Update the display
        Display.update();
    }

    /**
     * Draw HUD
     *
     * @param partialTicks Overflow ticks to interpolate
     */
    private void drawGui(float partialTicks) {
        // Clear depth
        glClear(GL_DEPTH_BUFFER_BIT);

        // Setup HUD camera
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        int screenWidth = this.width * 240 / this.height;
        int screenHeight = this.height * 240 / this.height;

        // Set camera perspective
        glOrtho(0.0, screenWidth, screenHeight, 0.0, 100.0F, 300.0F);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Move camera to Z level -200
        glTranslatef(0.0f, 0.0f, -200.0f);

        // Start tile display
        glPushMatrix();

        // Transform tile position to the top right corner
        glTranslated(screenWidth - 16, 16.0F, 0.0F);
        glScalef(16.0F, 16.0F, 16.0F);
        glRotatef(30.0F, 1.0F, 0.0F, 0.0F);
        glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
        glTranslatef(-1.5F, 0.5F, -0.5F);
        glScalef(-1.0F, -1.0F, 1.0F);

        // Setup tile rendering
        int id = Textures.loadTexture("/terrain.png", 9728);
        glBindTexture(GL_TEXTURE_2D, id);
        glEnable(GL_TEXTURE_2D);

        // Render selected tile in hand
        this.tessellator.init();
        Tile.tiles[this.selectedTileId].render(this.tessellator, this.level, 0, -2, 0, 0);
        this.tessellator.flush();

        // Render red quad inside the same context as the tile
        glDisable(GL_TEXTURE_2D);
        glColor4f(1.0F, 0.0F, 0.0F, 1.0F);
        glBegin(GL_QUADS);
        glVertex3f(-10.0F, -10.0F, 0.0F);
        glVertex3f(10.0F, -10.0F, 0.0F);
        glVertex3f(10.0F, 10.0F, 0.0F);
        glVertex3f(-10.0F, 10.0F, 0.0F);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        // Finish tile rendering
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();

        // Cross hair position
        int x = screenWidth / 2;
        int y = screenHeight / 2;

        // Cross hair color - changed to red to test if glColor4f works
        glColor4f(1.0F, 0.0F, 0.0F, 1.0F);

        // Render cross hair
        this.tessellator.init();
        this.tessellator.vertex((float) (x + 1), (float) (y - 4), 0.0F);
        this.tessellator.vertex((float) (x - 0), (float) (y - 4), 0.0F);
        this.tessellator.vertex((float) (x - 0), (float) (y + 5), 0.0F);
        this.tessellator.vertex((float) (x + 1), (float) (y + 5), 0.0F);
        this.tessellator.vertex((float) (x + 5), (float) (y - 0), 0.0F);
        this.tessellator.vertex((float) (x - 4), (float) (y - 0), 0.0F);
        this.tessellator.vertex((float) (x - 4), (float) (y + 1), 0.0F);
        this.tessellator.vertex((float) (x + 5), (float) (y + 1), 0.0F);
        this.tessellator.flush();
    }

    /**
     * Setup fog with type
     *
     * @param fogType Type of the fog. (0: daylight, 1: shadow)
     */
    private void setupFog(int fogType) {
        // Daylight fog
        if (fogType == 0) {
            // Fog distance
            glFogi(GL_FOG_MODE, GL_VIEWPORT_BIT);
            
            // Higher fog density in corrupted mode for darkness
            if (corruptedMode) {
                glFogf(GL_FOG_DENSITY, 0.05F);
            } else {
                glFogf(GL_FOG_DENSITY, 0.001F);
            }

            // Set fog color
            glFog(GL_FOG_COLOR, this.fogColorDaylight);

            glDisable(GL_LIGHTING);
        }

        // Shadow fog
        if (fogType == 1) {
            // Fog distance
            glFogi(GL_FOG_MODE, GL_VIEWPORT_BIT);
            
            // Higher fog density in corrupted mode for darkness
            if (corruptedMode) {
                glFogf(GL_FOG_DENSITY, 0.15F);
            } else {
                glFogf(GL_FOG_DENSITY, 0.06F);
            }

            // Set fog color
            glFog(GL_FOG_COLOR, this.fogColorShadow);

            glEnable(GL_LIGHTING);
            glEnable(GL_COLOR_MATERIAL);

            // Lower brightness in corrupted mode
            float brightness = corruptedMode ? 0.2F : 0.6F;
            glLightModel(GL_LIGHT_MODEL_AMBIENT, this.getBuffer(brightness, brightness, brightness, 1.0F));
        }
    }

    /**
     * Fill float buffer with color values and return it
     *
     * @param red   Red value
     * @param green Green value
     * @param blue  Blue value
     * @param alpha Alpha value
     * @return Float buffer filled in RGBA order
     */
    private FloatBuffer getBuffer(float red, float green, float blue, float alpha) {
        this.colorBuffer.clear();
        this.colorBuffer.put(red).put(green).put(blue).put(alpha);
        this.colorBuffer.flip();
        return this.colorBuffer;
    }

    /**
     * Check for corruption file
     */
    private void checkCorruptionFile() {
        try {
            java.io.File corruptionFile = new java.io.File("corruption.txt");
            if (corruptionFile.exists()) {
                java.util.Scanner scanner = new java.util.Scanner(corruptionFile);
                String content = scanner.nextLine();
                scanner.close();
                
                if (content.equals("1")) {
                    corruptedMode = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Execute corruption finale
     */
    private void executeCorruptionFinale() {
        try {
            // Save the level before deleting
            if (this.level != null) {
                this.level.save();
            }

            // Delete level.dat
            java.io.File levelFile = new java.io.File("level.dat");
            if (levelFile.exists()) {
                levelFile.delete();
            }

            // Create corruption file with "1"
            java.io.PrintWriter writer = new java.io.PrintWriter("corruption.txt");
            writer.println("1");
            writer.close();

            // Stop the game
            this.running = false;

            // Restart the game
            restartGame();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Restart the game
     */
    private void restartGame() {
        try {
            // Save the level before restarting
            if (this.level != null) {
                this.level.save();
            }
            
            // Get the main class and restart
            String javaBin = System.getProperty("java.home") + "/bin/java";
            String classpath = System.getProperty("java.class.path");
            String className = Minecraft.class.getName();

            ProcessBuilder builder = new ProcessBuilder(javaBin, "-cp", classpath, className);
            builder.start();
            
            // Exit current instance
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Entry point of the game
     *
     * @param args Program arguments (unused)
     */
    public static void main(String[] args) {
        // Extract native libraries before LWJGL initialization
        extractNatives();

        boolean fullScreen = true;

        // Find fullscreen argument
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-fullscreen")) {
                fullScreen = true;
                break;
            }
        }

        // Launch
        new Thread(new Minecraft(null, 1024, 680, fullScreen)).start();
    }

}
