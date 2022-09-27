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
import java.util.concurrent.locks.ReentrantLock;

import static support.PlayerWindow.*;

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

    // Guarda as músicas da lista de reprodução
    private Song[] songs;

    // Guarda as informações das músicas da lista de reprodução
    private String[][] songsInfo;

    // Guarda o index da música que está sendo reproduzida
    private int currentSongIndex;
    
    private int currentFrame = 0;

    // Lock utilizado para controlar os acessos às bitstreams
    private ReentrantLock bitstreamLock = new ReentrantLock();

    // True quando a música está pausada
    private boolean paused = false;

    // Thread usada para reproduzir músicas
    private Thread playThread = new Thread();

    // Começa a reproduzir a música selecionada
    private final ActionListener buttonListenerPlayNow = e -> {
        Thread playNowThread = new Thread(() -> {
            stopPlaying();

            startPlaying(getSelectedSongIndex());
        });

        playNowThread.start();
    };

    // Remove a música selecionada
    private final ActionListener buttonListenerRemove = e ->  {
        Thread removeThread = new Thread( () -> {
            int selectedSongIndex = getSelectedSongIndex();

            // Se a música deletada for a que estiver tocando, a thread de reprodução deve ser parada
            if (selectedSongIndex == currentSongIndex) {
                deleteSong(selectedSongIndex);

                stopPlaying();
            }
            else {
                deleteSong(selectedSongIndex);
            }
        });

        removeThread.start();
    };

    // Adiciona uma música ao final da lista de reprodução
    private final ActionListener buttonListenerAddSong = e -> {
        try {
            Song addedSong = window.openFileChooser();

            String[] addedSongInfo = addedSong.getDisplayInfo();

            // Adiciona a música no array songs
            if (songs != null) {
                songs = Arrays.copyOf(songs, songs.length + 1);
                songs[songs.length -1] = addedSong;
            }

            else {
                songs = new Song[]{addedSong};
            }

            // Adiciona as informações da música no array songsInfo
            if (songsInfo != null){
                songsInfo = Arrays.copyOf(songsInfo, songsInfo.length + 1);
                songsInfo[songsInfo.length -1] = addedSongInfo;
            }

            else {
                songsInfo = new String[][]{addedSongInfo};
            }

            // Atualiza a lista de reprodução
            window.setQueueList(songsInfo);

        } catch (IOException | InvalidDataException | BitstreamException | UnsupportedTagException ex) {
            throw new RuntimeException(ex);
        }
    };

    // Botão de pausar/despausar
    private final ActionListener buttonListenerPlayPause = e -> {
        if (paused) {
            // Deixa o botão Play/Pause com o ícone de pause
            window.setPlayPauseButtonIcon(1);

            // Despausa a música
            paused = false;
        }

        else {
            // Deixa o botão Play/Pause com o ícone de play
            window.setPlayPauseButtonIcon(0);

            // Pausa a música
            paused = true;
        }
    };

    // Para a reprodução atual
    private final ActionListener buttonListenerStop = e -> {
        Thread stopThread = new Thread( () -> {
            stopPlaying();
        });

        stopThread.start();
    };

    private final ActionListener buttonListenerNext = e -> {
        Thread nextThread = new Thread(() -> {
            stopPlaying();

            startPlaying(currentSongIndex + 1);
        });

        nextThread.start();
    };
    private final ActionListener buttonListenerPrevious = e -> {
        Thread previousThread = new Thread(() -> {
            stopPlaying();

            startPlaying(currentSongIndex - 1);
        });

        previousThread.start();
    };
    private final ActionListener buttonListenerShuffle = e -> {
        // TODO
    };
    private final ActionListener buttonListenerLoop = e -> {
        // TODO
    };
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
        bitstreamLock.lock();

        try {
            if (device != null) {
                Header h = bitstream.readFrame();
                if (h == null) return false;

                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
                device.write(output.getBuffer(), 0, output.getBufferLength());
                bitstream.closeFrame();
            }
            return true;
        }
        finally {
            bitstreamLock.unlock();
        }
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        bitstreamLock.lock();

        try {
            Header h = bitstream.readFrame();
            if (h == null) return false;
            bitstream.closeFrame();
            currentFrame++;
            return true;
        }
        finally {
            bitstreamLock.unlock();
        }
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        bitstreamLock.lock();

        try {
            if (newFrame > currentFrame) {
                int framesToSkip = newFrame - currentFrame;
                boolean condition = true;
                while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
            }
        }
        finally {
            bitstreamLock.unlock();
        }
    }

    // Função usada para interromper a reprodução das músicas
    private void stopPlaying() {
        // Interrompe a thread
        playThread.interrupt();

        // Fecha o device e a bitstream
        if (bitstream != null) {
            try {
                bitstream.close();
                device.close();
            } catch (BitstreamException bitstreamException) {
                bitstreamException.printStackTrace();
            }
        }

        // Atualiza a GUI
        EventQueue.invokeLater( () -> {
            // Deixa o botão "Play/Pause" desabilitado e com ícone de Play
            window.setEnabledPlayPauseButton(false);
            window.setPlayPauseButtonIcon(0);

            // Desabilita o botão Stop
            window.setEnabledStopButton(false);

            // Deixa a aba de informações da música em branco
            window.resetMiniPlayer();
        });

        paused = false;
    }

    // Função usada para iniciar a reprodução das músicas
    private void startPlaying(int songIndex) {
        currentSongIndex = songIndex;

        Song selected_song = songs[songIndex];

        // Cria a bitstream e o device
        try {
            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(this.decoder = new Decoder());
            bitstream = new Bitstream(selected_song.getBufferedInputStream());
        }
        catch (JavaLayerException | FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }

        currentFrame = 0;

        // Atualiza a GUI
        EventQueue.invokeLater( () -> {
            // Atualiza os botões Previous & Next
            if (songs.length == 1) {
                window.setEnabledPreviousButton(false);
                window.setEnabledNextButton(false);
            }
            else if (currentSongIndex == 0) {
                window.setEnabledPreviousButton(false);
                window.setEnabledNextButton(true);
            }
            else if (currentSongIndex == songs.length - 1) {
                window.setEnabledPreviousButton(true);
                window.setEnabledNextButton(false);
            }
            else {
                window.setEnabledPreviousButton(true);
                window.setEnabledNextButton(true);
            }

            // Deixa o botão "Play/Pause" habilitado e com ícone de Pause
            window.setEnabledPlayPauseButton(true);
            window.setPlayPauseButtonIcon(1);

            // Habilita o botão Stop
            window.setEnabledStopButton(true);

            // Mostra as informações da música
            window.setPlayingSongInfo(songsInfo[songIndex][0], songsInfo[songIndex][1], songsInfo[songIndex][2]);
        });

        // Inicia a thread
        playThread = new Thread(() -> {
            // Tempo total da música
            int totalTime = (int) songs[currentSongIndex].getMsLength();
            while (true) {
                try {
                    if (!paused) {
                        // Tempo atual da música
                        int currentTime = (int) (songs[currentSongIndex].getMsPerFrame() * currentFrame);

                        // Contador de tempo
                        EventQueue.invokeLater(() -> {
                            window.setTime(currentTime, totalTime);
                        });

                        if (!playNextFrame()) {
                            // Toca a próxima música em sequência (se houver)
                            if (currentSongIndex < songs.length - 1) {
                                Thread playNextThread = new Thread(() -> {
                                    stopPlaying();

                                    startPlaying(currentSongIndex + 1);
                                });

                                playNextThread.start();
                            }
                            else {
                                stopPlaying();
                            }
                        }

                        currentFrame++;
                    }
                }
                catch (JavaLayerException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        playThread.start();
    }
    //</editor-fold>

    // Retorna o index na lista de reprodução da música selecionada
    private int getSelectedSongIndex() {
        for (int i = 0; i < songsInfo.length; i++) {
            String address = songsInfo[i][5];
            if (Objects.equals(address, window.getSelectedSong())){ // Musica clicada
                return i;
            }
        }
        return 0;
    }

    // Deleta a música selecionada da lista de reprodução
    private void deleteSong(int index){
        // Faz cópias dos arrays songs e songsInfo deixando de fora o index a ser excluído
        Song[] auxSongs = new Song[songs.length-1];

        String[][] auxSongsInfo = new String[songsInfo.length -1][];

        int counter = 0;

        for (int i = 0; i < songs.length; i++){
            if(i != index){
                auxSongs[counter] = songs[i];
                auxSongsInfo[counter] = songsInfo[i];
                counter ++;
            }
        }

        songs = auxSongs;
        songsInfo = auxSongsInfo;

        // Atualiza o currentSongIndex
        if (index < currentSongIndex) {
            currentSongIndex--;
        }

        EventQueue.invokeLater( () -> {
            // Atualiza a lista de reprodução
            window.setQueueList(songsInfo);
        });
    }
}
