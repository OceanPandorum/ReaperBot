package reaper;

import arc.files.Fi;
import arc.struct.*;
import arc.util.pooling.Pool;
import mindustry.Vars;
import mindustry.game.*;
import mindustry.world.Block;
import mindustry.world.blocks.logic.LogicBlock;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;

/**
 * Конвертер изображений в логический код,
 * переделанный под манер использования ботом. <br>
 *
 * @author owler0954,
 */
public class LContentHandler{
    private static final String SCHEM_PROC = "bXNjaAF4nGNgZGBkYmDJS8xNZeC4sP9iw4V9F9sYuFNSi5OLMgtKMvPzGBgYGfhzM5OL8nULivKTU4uL84sYQIIgwAfE3BVzklMamBgYhBiYGADInhTo";
    private static final String SCHEM_CELL = "bXNjaAF4nGNgZGBkYmDJS8xNZeC42HFhx4XtFzYwcKekFicXZRaUZObnMTAwMnDnpubmF1XqJqfm5DCABCAAAEaXELw=";
    private static final String SCHEM_DISPLAY = "bXNjaAF4nGNgY2BjYmDJS8xNZWBPySwuyEmsZOBOSS1OLsosKMnMz2NgYGQQzkksSk/VzclPz0zWhSkCSTAwASEDAF42EOI=";

    public Fi convert(HandlerSpec spec){
        return spec.forward ? forward(spec) : def(spec);
    }

    private Fi def(HandlerSpec spec){
        try{
            Fi file = Constants.cacheDir.child("output-" + UUID.randomUUID().toString() + ".masm");
            int w = spec.image.getWidth();
            int h = spec.image.getHeight();
            int[][] pictureR = new int[w][h];
            int[][] pictureG = new int[w][h];
            int[][] pictureB = new int[w][h];
            int drawCalls = 0;

            Writer writer = file.writer(true);

            for(int x = 0; x < w; x++){
                for(int y = 0; y < h; y++){
                    int rgb = spec.image.getRGB(x, y);
                    pictureR[x][y] = (rgb >> 16) & 0xFF;
                    pictureG[x][y] = (rgb >> 8) & 0xFF;
                    pictureB[x][y] = (rgb) & 0xFF;

                    if((drawCalls + 2) / spec.displayBufferSize > 0){
                        writer.append("drawflush display1\n");
                        drawCalls = 0;
                    }

                    writer.append("draw color ").append(String.valueOf(pictureR[x][y]))
                            .append(" ").append(String.valueOf(pictureG[x][y]))
                            .append(" ").append(String.valueOf(pictureB[x][y]))
                            .append(" 0 0\n");

                    writer.append("draw rect ").append(String.valueOf(x * spec.multiplier))
                            .append(" ").append(String.valueOf((h - y - 1) * spec.multiplier))
                            .append(" ").append(String.valueOf(spec.multiplier))
                            .append(" ").append(String.valueOf(spec.displayBufferSize))
                            .append(" 0\n");

                    drawCalls += 2;
                }

                writer.append("drawflush display1\n");
                writer.flush();
            }

            return file;
        }catch(IOException e){
            return null;
        }
    }

    private Fi forward(HandlerSpec spec){
        //todo
        return null;
    }

    public static class HandlerSpec{
        public BufferedImage image;
        public int displayBufferSize;
        public int multiplier;
        public boolean forward;

        public HandlerSpec(BufferedImage image, int displayBufferSize, int multiplier, boolean forward){
            this.image = image;
            this.displayBufferSize = displayBufferSize;
            this.multiplier = multiplier;
            this.forward = forward;
        }
    }
}
