import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;

    private String windowTitle = "Deezer Inc.";

    private String[][] songsInfo;

    private Song[] songs;

    private int currentFrame = 0;

    private Thread playThread;

    private final ActionListener buttonListenerPlayNow = e -> {
        int real_position = -1;

        for (int i = 0; i < songsInfo.length; i++) {
            String address = songsInfo[i][5];
            if (Objects.equals(address, window.getSelectedSong())){
                real_position = i;
            }
        }

        Song selected_song = songs[real_position];

        try {
            this.device = FactoryRegistry.systemRegistry().createAudioDevice();
            this.device.open(this.decoder = new Decoder());
            this.bitstream = new Bitstream(selected_song.getBufferedInputStream());
        } catch (JavaLayerException ex) {
            throw new RuntimeException(ex);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
        currentFrame = 0;
        int finalReal_position = real_position;

        Thread playThread = new Thread((int final_real_position)
                -> {
            while (true) {
                window.setPlayingSongInfo(songsInfo[final_real_position][0], songsInfo[final_real_position][1], songsInfo[final_real_position][2]);
                try {
                    if (!playNextFrame()) break;
                } catch (JavaLayerException ex) {
                    throw new RuntimeException(ex);
                }

            }
        });

        playThread.start();
        playThread.interrupt();

    };
    private final ActionListener buttonListenerRemove =
            e -> new Thread(() -> {
                });
    private final ActionListener buttonListenerAddSong = e -> {
        try {
            Song addedSong = window.openFileChooser();

            String[] addedSongInfo = addedSong.getDisplayInfo();

            if (songs != null) {
                songs = Arrays.copyOf(songs, songs.length + 1);
                songs[songs.length -1] = addedSong;
            }

            else {
                songs = new Song[]{addedSong};
            }

            if (songsInfo != null){
                songsInfo = Arrays.copyOf(songsInfo, songsInfo.length + 1);
                songsInfo[songsInfo.length -1] = addedSongInfo;
            }

            else {
                songsInfo = new String[][]{addedSongInfo};
            }

            window.setQueueList(songsInfo);

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (BitstreamException ex) {
            throw new RuntimeException(ex);
        } catch (UnsupportedTagException ex) {
            throw new RuntimeException(ex);
        } catch (InvalidDataException ex) {
            throw new RuntimeException(ex);
        }
    };
    private final ActionListener buttonListenerPlayPause =
            e -> new Thread(() -> {
                });
    private final ActionListener buttonListenerStop =
            e -> new Thread(() -> {
                });
    private final ActionListener buttonListenerNext =
            e -> new Thread(() -> {
                });
    private final ActionListener buttonListenerPrevious =
            e -> new Thread(() -> {
                });
    private final ActionListener buttonListenerShuffle =
            e -> new Thread(() -> {
                });
    private final ActionListener buttonListenerLoop =
            e -> new Thread(() -> {
                });
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> window = new PlayerWindow(
                windowTitle,
                songsInfo,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>
}
