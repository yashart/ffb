package com.fumbbl.ffb.client;

import com.fumbbl.ffb.BloodSpot;
import com.fumbbl.ffb.DiceDecoration;
import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.MoveSquare;
import com.fumbbl.ffb.PushbackSquare;
import com.fumbbl.ffb.RangeRuler;
import com.fumbbl.ffb.TrackNumber;
import com.fumbbl.ffb.Weather;
import com.fumbbl.ffb.client.layer.FieldLayerBloodspots;
import com.fumbbl.ffb.client.layer.FieldLayerEnhancements;
import com.fumbbl.ffb.client.layer.FieldLayerMarker;
import com.fumbbl.ffb.client.layer.FieldLayerOverPlayers;
import com.fumbbl.ffb.client.layer.FieldLayerPitch;
import com.fumbbl.ffb.client.layer.FieldLayerPlayers;
import com.fumbbl.ffb.client.layer.FieldLayerRangeGrid;
import com.fumbbl.ffb.client.layer.FieldLayerRangeRuler;
import com.fumbbl.ffb.client.layer.FieldLayerSketches;
import com.fumbbl.ffb.client.layer.FieldLayerTackleZones;
import com.fumbbl.ffb.client.layer.FieldLayerTeamLogo;
import com.fumbbl.ffb.client.layer.FieldLayerUnderPlayers;
import com.fumbbl.ffb.client.overlay.Overlay;
import com.fumbbl.ffb.client.overlay.sketch.ClientSketchManager;
import com.fumbbl.ffb.client.state.ClientState;
import com.fumbbl.ffb.client.state.logic.LogicModule;
import com.fumbbl.ffb.marking.FieldMarker;
import com.fumbbl.ffb.marking.PlayerMarker;
import com.fumbbl.ffb.model.FieldModel;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.Player;
import com.fumbbl.ffb.model.change.IModelChangeObserver;
import com.fumbbl.ffb.model.change.ModelChange;
import com.fumbbl.ffb.model.sketch.SketchState;
import com.fumbbl.ffb.model.stadium.OnPitchEnhancement;

import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import javax.swing.event.MouseInputListener;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author j129340
 */
public class FieldComponent extends JPanel implements IModelChangeObserver, MouseInputListener, MouseWheelListener {

    private final FantasyFootballClient fClient;

    private final FieldLayerPitch fLayerField;
    private final FieldLayerTeamLogo fLayerTeamLogo;
    private final FieldLayerBloodspots fLayerBloodspots;
    private final FieldLayerRangeGrid fLayerRangeGrid;
    private final FieldLayerMarker fLayerMarker;
    private final FieldLayerUnderPlayers fLayerUnderPlayers;
    private final FieldLayerPlayers fLayerPlayers;
    private final FieldLayerOverPlayers fLayerOverPlayers;
    private final FieldLayerRangeRuler fLayerRangeRuler;
    private final FieldLayerEnhancements layerEnhancements;
    private final FieldLayerSketches layerSketches;
    private final FieldLayerTackleZones layerTackleZones;
    private BufferedImage fImage;

    // we need to keep some old model values for a redraw (if those get set to null)
    private FieldCoordinate fBallCoordinate;
    private FieldCoordinate fBombCoordinate;
    private final Map<String, FieldCoordinate> fCoordinateByPlayerId;

    private final UiDimensionProvider uiDimensionProvider;
    private final PitchDimensionProvider pitchDimensionProvider;

    // ====================== VIEWPORT ======================
    private boolean viewportEnabled;
    private int viewportX, viewportY;             // top-left corner in fImage pixels
    private int viewportWidth, viewportHeight;     // viewport size in fImage pixels
    private int visibleSquaresX, visibleSquaresY;  // stored for recalc on layout change

    // Panning with middle mouse button
    private Point panDragStart;
    private int panStartVpX, panStartVpY;

    // Minimap
    private boolean minimapEnabled = true;
    private static final double MINIMAP_SCALE = 0.1;  // 20% of component width
    private static final int MINIMAP_MARGIN = 4;
    // ======================================================

    public FieldComponent(FantasyFootballClient pClient, UiDimensionProvider uiDimensionProvider,
                          PitchDimensionProvider pitchDimensionProvider, FontCache fontCache,
                          ClientSketchManager sketchManager, StyleProvider styleProvider) {

        fClient = pClient;
        this.uiDimensionProvider = uiDimensionProvider;
        this.pitchDimensionProvider = pitchDimensionProvider;

        fLayerField = new FieldLayerPitch(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        fLayerTeamLogo = new FieldLayerTeamLogo(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        fLayerBloodspots = new FieldLayerBloodspots(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        fLayerRangeGrid = new FieldLayerRangeGrid(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        fLayerMarker = new FieldLayerMarker(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        fLayerUnderPlayers = new FieldLayerUnderPlayers(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        fLayerPlayers = new FieldLayerPlayers(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        fLayerOverPlayers = new FieldLayerOverPlayers(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        fLayerRangeRuler = new FieldLayerRangeRuler(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        layerEnhancements = new FieldLayerEnhancements(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache);
        layerSketches = new FieldLayerSketches(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache, sketchManager);
        layerTackleZones = new FieldLayerTackleZones(pClient, uiDimensionProvider, pitchDimensionProvider, fontCache, styleProvider);

        fCoordinateByPlayerId = new HashMap<>();

        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);  // NEW

        ToolTipManager.sharedInstance().registerComponent(this);

        refresh();

    }

    // ====================== VIEWPORT API ======================

    /**
     * Enable viewport mode — show only visibleX × visibleY squares.
     * Coordinates are in screen squares (after portrait rotation if applicable).
     *
     * @param visibleX number of squares visible horizontally
     * @param visibleY number of squares visible vertically
     */
    public void enableViewport(int visibleX, int visibleY) {
        this.visibleSquaresX = visibleX;
        this.visibleSquaresY = visibleY;
        this.viewportEnabled = true;
        recalculateViewport();
    }

    /**
     * Disable viewport — show the full field.
     */
    public void disableViewport() {
        this.viewportEnabled = false;
        updateComponentSize();
        repaint();
    }

    public boolean isViewportEnabled() {
        return viewportEnabled;
    }

    /**
     * Center the viewport on a field coordinate (game-logical coordinate).
     * Uses PitchDimensionProvider to handle portrait rotation.
     */
    public void centerViewportOn(FieldCoordinate coord) {
        if (!viewportEnabled || coord == null) return;
        Dimension local = pitchDimensionProvider.mapToLocal(coord, true);
        // local.width = pixel X on fImage, local.height = pixel Y on fImage
        setViewportPosition(local.width - viewportWidth / 2,
                local.height - viewportHeight / 2);
        repaint();
    }

    /**
     * Check if a field coordinate is currently visible in the viewport.
     */
    public boolean isInViewport(FieldCoordinate coord) {
        if (!viewportEnabled || coord == null) return true;
        Dimension local = pitchDimensionProvider.mapToLocal(coord, true);
        int margin = pitchDimensionProvider.fieldSquareSize(); // 1-square margin
        return local.width >= viewportX + margin
                && local.width < viewportX + viewportWidth - margin
                && local.height >= viewportY + margin
                && local.height < viewportY + viewportHeight - margin;
    }

    /**
     * Pan the viewport by dx, dy pixels (in fImage/screen space).
     */
    public void panViewport(int dx, int dy) {
        setViewportPosition(viewportX + dx, viewportY + dy);
        repaint();
    }

    public void setMinimapEnabled(boolean enabled) {
        this.minimapEnabled = enabled;
        repaint();
    }

    private void recalculateViewport() {
        if (!viewportEnabled) return;

        int squareSize = pitchDimensionProvider.fieldSquareSize();
        Dimension fullSize = uiDimensionProvider.dimension(Component.FIELD);

        viewportWidth = Math.min(visibleSquaresX * squareSize, fullSize.width);
        viewportHeight = Math.min(visibleSquaresY * squareSize, fullSize.height);

        // Center viewport on the field
        viewportX = Math.max(0, (fullSize.width - viewportWidth) / 2);
        viewportY = Math.max(0, (fullSize.height - viewportHeight) / 2);

        updateComponentSize();
    }

    private void setViewportPosition(int x, int y) {
        Dimension fullSize = uiDimensionProvider.dimension(Component.FIELD);
        int maxX = Math.max(0, fullSize.width - viewportWidth);
        int maxY = Math.max(0, fullSize.height - viewportHeight);
        viewportX = Math.max(0, Math.min(x, maxX));
        viewportY = Math.max(0, Math.min(y, maxY));
    }

    private void updateComponentSize() {
        Dimension size;
        if (viewportEnabled) {
            size = new Dimension(viewportWidth, viewportHeight);
        } else {
            size = uiDimensionProvider.dimension(Component.FIELD);
        }
        setMinimumSize(size);
        setPreferredSize(size);
        setMaximumSize(size);
        revalidate();
    }

    /**
     * Translate a MouseEvent from component (viewport) coordinates
     * to fImage (full field) coordinates.
     * All downstream code (ClientState, Overlay, getFieldCoordinate)
     * sees coordinates as if the full field were displayed.
     */
    private MouseEvent translateMouseEvent(MouseEvent e) {
        if (!viewportEnabled) return e;
        return new MouseEvent(
                e.getComponent(),
                e.getID(), e.getWhen(), e.getModifiersEx(),
                e.getX() + viewportX,       // translated X
                e.getY() + viewportY,       // translated Y
                e.getXOnScreen(), e.getYOnScreen(),
                e.getClickCount(), e.isPopupTrigger(), e.getButton()
        );
    }

    // ====================== END VIEWPORT ======================

    public void initLayout() {

        fLayerField.initLayout();
        fLayerTeamLogo.initLayout();
        fLayerBloodspots.initLayout();
        fLayerRangeGrid.initLayout();
        fLayerMarker.initLayout();
        fLayerUnderPlayers.initLayout();
        fLayerPlayers.initLayout();
        fLayerOverPlayers.initLayout();
        fLayerRangeRuler.initLayout();
        layerEnhancements.initLayout();
        layerSketches.initLayout();
        layerTackleZones.initLayout();

        // fImage is ALWAYS full field size — layers draw into full field
        Dimension fullSize = uiDimensionProvider.dimension(Component.FIELD);
        fImage = new BufferedImage(fullSize.width, fullSize.height, BufferedImage.TYPE_INT_ARGB);

        // Component size depends on viewport
        if (viewportEnabled) {
            recalculateViewport();
        } else {
            setMinimumSize(fullSize);
            setPreferredSize(fullSize);
            setMaximumSize(fullSize);
        }

    }

    public FieldLayerPitch getLayerField() {
        return fLayerField;
    }

    public FieldLayerTeamLogo getLayerTeamLogo() {
        return fLayerTeamLogo;
    }

    public FieldLayerBloodspots getLayerBloodspots() {
        return fLayerBloodspots;
    }

    public FieldLayerRangeGrid getLayerRangeGrid() {
        return fLayerRangeGrid;
    }

    public FieldLayerMarker getLayerMarker() {
        return fLayerMarker;
    }

    public FieldLayerUnderPlayers getLayerUnderPlayers() {
        return fLayerUnderPlayers;
    }

    public FieldLayerPlayers getLayerPlayers() {
        return fLayerPlayers;
    }

    public FieldLayerOverPlayers getLayerOverPlayers() {
        return fLayerOverPlayers;
    }

    public FieldLayerRangeRuler getLayerRangeRuler() {
        return fLayerRangeRuler;
    }

    public FieldLayerEnhancements getLayerEnhancements() {
        return layerEnhancements;
    }

    public FieldLayerSketches getLayerSketches() {
        return layerSketches;
    }

    public FieldLayerTackleZones getLayerTackleZones() {
        return layerTackleZones;
    }

    public synchronized void refresh() {

        Rectangle updatedArea = combineRectangles(new Rectangle[]{getLayerField().fetchUpdatedArea(),
                getLayerTeamLogo().fetchUpdatedArea(), getLayerEnhancements().fetchUpdatedArea(),
                getLayerBloodspots().fetchUpdatedArea(),
                getLayerRangeGrid().fetchUpdatedArea(), getLayerMarker().fetchUpdatedArea(),
                getLayerUnderPlayers().fetchUpdatedArea(), getLayerTackleZones().fetchUpdatedArea(),
                getLayerPlayers().fetchUpdatedArea(), getLayerOverPlayers().fetchUpdatedArea(),
                getLayerRangeRuler().fetchUpdatedArea(), getLayerSketches().fetchUpdatedArea(),
        });

        if (updatedArea != null) {
            refresh(updatedArea);
        }

    }

    public synchronized void refresh(Rectangle pUpdatedArea) {

        Graphics2D g2d = fImage.createGraphics();

        if (pUpdatedArea != null) {
            g2d.setClip(pUpdatedArea.x, pUpdatedArea.y, pUpdatedArea.width, pUpdatedArea.height);
        }

        g2d.drawImage(getLayerField().getImage(), 0, 0, null);
        g2d.drawImage(getLayerTeamLogo().getImage(), 0, 0, null);
        g2d.drawImage(getLayerEnhancements().getImage(), 0, 0, null);
        g2d.drawImage(getLayerBloodspots().getImage(), 0, 0, null);
        g2d.drawImage(getLayerRangeGrid().getImage(), 0, 0, null);
        g2d.drawImage(getLayerMarker().getImage(), 0, 0, null);
        g2d.drawImage(getLayerUnderPlayers().getImage(), 0, 0, null);
        g2d.drawImage(getLayerTackleZones().getImage(), 0, 0, null);
        g2d.drawImage(getLayerPlayers().getImage(), 0, 0, null);
        g2d.drawImage(getLayerOverPlayers().getImage(), 0, 0, null);
        g2d.drawImage(getLayerRangeRuler().getImage(), 0, 0, null);
        g2d.drawImage(getLayerSketches().getImage(), 0, 0, null);

        g2d.dispose();

        // VIEWPORT-AWARE REPAINT
        if (viewportEnabled && pUpdatedArea != null) {
            // Translate fImage coords → component coords
            Rectangle compRect = new Rectangle(
                    pUpdatedArea.x - viewportX,
                    pUpdatedArea.y - viewportY,
                    pUpdatedArea.width,
                    pUpdatedArea.height
            );
            Rectangle compBounds = new Rectangle(0, 0, viewportWidth, viewportHeight);
            if (compRect.intersects(compBounds)) {
                repaint(compRect.intersection(compBounds));
            }
        } else if (pUpdatedArea != null) {
            repaint(pUpdatedArea);
        } else {
            repaint();
        }

    }

    public synchronized void update(ModelChange pModelChange) {
        if ((pModelChange == null) || (pModelChange.getChangeId() == null)) {
            return;
        }
        Game game = getClient().getGame();
        FieldModel fieldModel = game.getFieldModel();
        switch (pModelChange.getChangeId()) {
            case FIELD_MODEL_ADD_BLOOD_SPOT:
                getLayerBloodspots().drawBloodspot((BloodSpot) pModelChange.getValue());
                break;
            case FIELD_MODEL_ADD_DICE_DECORATION:
                getLayerOverPlayers().drawDiceDecoration((DiceDecoration) pModelChange.getValue());
                break;
            case FIELD_MODEL_ADD_FIELD_MARKER:
                getLayerMarker().drawFieldMarker((FieldMarker) pModelChange.getValue());
                break;
            case FIELD_MODEL_ADD_MOVE_SQUARE:
                getLayerOverPlayers().drawMoveSquare((MoveSquare) pModelChange.getValue());
                break;
            case FIELD_MODEL_ADD_PLAYER_MARKER:
                getLayerPlayers().updatePlayerMarker((PlayerMarker) pModelChange.getValue());
                break;
            case FIELD_MODEL_ADD_PUSHBACK_SQUARE:
                getLayerOverPlayers().drawPushbackSquare((PushbackSquare) pModelChange.getValue());
                break;
            case FIELD_MODEL_ADD_TRACK_NUMBER:
                getLayerUnderPlayers().drawTrackNumber((TrackNumber) pModelChange.getValue());
                break;
            case FIELD_MODEL_ADD_TRAP_DOOR:
                getLayerEnhancements().addEnhancement((OnPitchEnhancement) pModelChange.getValue());
                break;
            case FIELD_MODEL_REMOVE_DICE_DECORATION:
                getLayerOverPlayers().removeDiceDecoration((DiceDecoration) pModelChange.getValue());
                break;
            case FIELD_MODEL_REMOVE_FIELD_MARKER:
                getLayerMarker().removeFieldMarker((FieldMarker) pModelChange.getValue());
                break;
            case FIELD_MODEL_REMOVE_MOVE_SQUARE:
                getLayerOverPlayers().removeMoveSquare((MoveSquare) pModelChange.getValue());
                break;
            case FIELD_MODEL_REMOVE_PLAYER_MARKER:
                getLayerPlayers().updatePlayerMarker((PlayerMarker) pModelChange.getValue());
                break;
            case FIELD_MODEL_REMOVE_PUSHBACK_SQUARE:
                getLayerOverPlayers().removePushbackSquare((PushbackSquare) pModelChange.getValue());
                break;
            case FIELD_MODEL_REMOVE_TRACK_NUMBER:
                getLayerUnderPlayers().removeTrackNumber((TrackNumber) pModelChange.getValue());
                break;
            case FIELD_MODEL_REMOVE_TRAP_DOOR:
                getLayerEnhancements().removeEnhancement((OnPitchEnhancement) pModelChange.getValue());
                break;
            case FIELD_MODEL_SET_BALL_COORDINATE:
                if (fBallCoordinate != null) {
                    getLayerPlayers().updateBallAndPlayers(fBallCoordinate, false);
                }
                FieldCoordinate ballCoordinate = (FieldCoordinate) pModelChange.getValue();
                if (ballCoordinate != null) {
                    getLayerPlayers().updateBallAndPlayers(ballCoordinate, false);
                }
                fBallCoordinate = ballCoordinate;
                // AUTO-FOLLOW: scroll viewport to ball if it's outside visible area
                /*
                if (viewportEnabled && ballCoordinate != null && !isInViewport(ballCoordinate)) {
                    centerViewportOn(ballCoordinate);
                }*/
                break;
            case FIELD_MODEL_SET_BALL_MOVING:
                getLayerPlayers().updateBallAndPlayers(fieldModel.getBallCoordinate(), false);
                break;
            case FIELD_MODEL_SET_OUT_OF_BOUNDS:
                if (fBombCoordinate != null) {
                    getLayerPlayers().updateBallAndPlayers(fBombCoordinate, false);
                } else {
                    getLayerPlayers().updateBallAndPlayers(fieldModel.getBallCoordinate(), false);
                }
                break;
            case FIELD_MODEL_SET_BOMB_COORDINATE:
                if (fBombCoordinate != null) {
                    getLayerPlayers().updateBallAndPlayers(fBombCoordinate, false);
                }
                FieldCoordinate bombCoordinate = (FieldCoordinate) pModelChange.getValue();
                if (bombCoordinate != null) {
                    getLayerPlayers().updateBallAndPlayers(bombCoordinate, false);
                }
                fBombCoordinate = bombCoordinate;
                break;
            case FIELD_MODEL_SET_BOMB_MOVING:
                getLayerPlayers().updateBallAndPlayers(fieldModel.getBombCoordinate(), false);
                break;
            case FIELD_MODEL_SET_PLAYER_COORDINATE:
                FieldCoordinate oldPlayerCoordinate = fCoordinateByPlayerId.get(pModelChange.getKey());
                getLayerTackleZones().init();
                if (oldPlayerCoordinate != null) {
                    getLayerPlayers().updateBallAndPlayers(oldPlayerCoordinate, true);
                }
                FieldCoordinate playerCoordinate = (FieldCoordinate) pModelChange.getValue();
                if (playerCoordinate != null) {
                    getLayerPlayers().updateBallAndPlayers(playerCoordinate, true);
                }
                fCoordinateByPlayerId.put(pModelChange.getKey(), playerCoordinate);
                break;
            case FIELD_MODEL_SET_PLAYER_STATE:
                Player<?> player = game.getPlayerById(pModelChange.getKey());
                FieldCoordinate playerCoordinateForStateChange = fieldModel.getPlayerCoordinate(player);
                boolean playerOverBall = fieldModel.isBallInPlay();
                getLayerTackleZones().init();
                getLayerPlayers().updateBallAndPlayers(playerCoordinateForStateChange, playerOverBall);
                break;
            case FIELD_MODEL_SET_RANGE_RULER:
                getLayerRangeRuler().drawRangeRuler((RangeRuler) pModelChange.getValue());
                break;
            case FIELD_MODEL_SET_WEATHER:
                getLayerField().drawWeather((Weather) pModelChange.getValue());
                break;
            case GAME_SET_SETUP_OFFENSE:
            case GAME_SET_HOME_PLAYING:
            case GAME_SET_TURN_MODE:
                getLayerUnderPlayers().init();
                getLayerTackleZones().init();
                break;
            case SKETCH_UPDATE:
                getLayerSketches().draw((SketchState) pModelChange.getValue());
                refresh();
                break;
            default:
                break;
        }
    }

    public synchronized void init() {
        Game game = getClient().getGame();
        game.addObserver(this);
        initPlayerCoordinates();
        getLayerField().init();
        getLayerTeamLogo().init();
        getLayerEnhancements().init();
        getLayerBloodspots().init();
        getLayerRangeGrid().init();
        getLayerMarker().init();
        getLayerUnderPlayers().init();
        getLayerPlayers().init();
        getLayerOverPlayers().init();
        getLayerRangeRuler().init();
        getLayerSketches().init();
        getLayerTackleZones().init();
        refresh();

    }

    private void initPlayerCoordinates() {
        Game game = getClient().getGame();
        for (Player<?> player : game.getPlayers()) {
            fCoordinateByPlayerId.put(player.getId(), game.getFieldModel().getPlayerCoordinate(player));
        }
    }

    private Rectangle combineRectangles(Rectangle[] pRectangles) {
        Rectangle result = null;
        for (Rectangle pRectangle : pRectangles) {
            if (pRectangle != null) {
                if (result != null) {
                    result.add(pRectangle);
                } else {
                    result = pRectangle;
                }
            }
        }
        return result;
    }

    // ====================== PAINTING ======================

    protected void paintComponent(Graphics pGraphics) {
        if (viewportEnabled) {
            // Draw only the viewport portion of fImage, mapped 1:1 to the component
            pGraphics.drawImage(fImage,
                    // destination (component coords)
                    0, 0, viewportWidth, viewportHeight,
                    // source (fImage coords)
                    viewportX, viewportY,
                    viewportX + viewportWidth, viewportY + viewportHeight,
                    null);

            // Draw minimap overlay
            if (minimapEnabled) {
                drawMinimap((Graphics2D) pGraphics);
            }
        } else {
            pGraphics.drawImage(fImage, 0, 0, null);
        }
    }

    /**
     * Draws a small minimap in the bottom-right corner showing the full field
     * with a yellow rectangle indicating the current viewport.
     */
    private void drawMinimap(Graphics2D g2) {
        if (fImage == null) return;

        int mmWidth = (int) (getWidth() * MINIMAP_SCALE);
        int mmHeight = (int) (mmWidth * ((double) fImage.getHeight() / fImage.getWidth()));
        int mmX = getWidth() - mmWidth - MINIMAP_MARGIN;
        int mmY = getHeight() - mmHeight - MINIMAP_MARGIN;

        // Semi-transparent background
        g2.setColor(new Color(0, 0, 0, 100));
        g2.fillRect(mmX - 1, mmY - 1, mmWidth + 2, mmHeight + 2);

        // Full field thumbnail
        g2.drawImage(fImage,
                mmX, mmY, mmX + mmWidth, mmY + mmHeight,
                0, 0, fImage.getWidth(), fImage.getHeight(),
                null);

        // Viewport rectangle
        double scaleX = (double) mmWidth / fImage.getWidth();
        double scaleY = (double) mmHeight / fImage.getHeight();

        int rectX = mmX + (int) (viewportX * scaleX);
        int rectY = mmY + (int) (viewportY * scaleY);
        int rectW = (int) (viewportWidth * scaleX);
        int rectH = (int) (viewportHeight * scaleY);

        g2.setColor(new Color(255, 255, 0, 200));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(rectX, rectY, rectW, rectH);

        // Border
        g2.setColor(new Color(255, 255, 255, 120));
        g2.setStroke(new BasicStroke(1));
        g2.drawRect(mmX - 1, mmY - 1, mmWidth + 1, mmHeight + 1);
    }

    // ====================== MOUSE HANDLING ======================

    // MouseMotionListener
    public void mouseMoved(MouseEvent pMouseEvent) {
        // Entropy uses raw screen coords — fine as-is
        getClient().getUserInterface().getMouseEntropySource().reportMousePosition(pMouseEvent);

        // Translate for game logic
        MouseEvent translated = translateMouseEvent(pMouseEvent);

        Optional<Overlay> overlay = getClient().getActiveOverlay();
        overlay.ifPresent(value -> value.mouseMoved(translated));

        ClientState<? extends LogicModule, ? extends FantasyFootballClient> uiState = getClient().getClientState();
        if (uiState != null) {
            uiState.mouseMoved(translated);
        }
    }

    // MouseMotionListener
    public void mouseDragged(MouseEvent pMouseEvent) {
        getClient().getUserInterface().getMouseEntropySource().reportMousePosition(pMouseEvent);

        // Handle viewport panning (middle mouse button drag)
        if (viewportEnabled && panDragStart != null) {
            int dx = panDragStart.x - pMouseEvent.getX();
            int dy = panDragStart.y - pMouseEvent.getY();
            setViewportPosition(panStartVpX + dx, panStartVpY + dy);
            repaint();
            return; // consume — don't forward pan drags to game logic
        }

        MouseEvent translated = translateMouseEvent(pMouseEvent);

        Optional<Overlay> overlay = getClient().getActiveOverlay();
        if (overlay.isPresent()) {
            overlay.get().mouseDragged(translated);
            return;
        }

        ClientState<? extends LogicModule, ? extends FantasyFootballClient> uiState = getClient().getClientState();
        if (uiState != null) {
            uiState.mouseDragged(translated);
        }
    }

    // MouseListener
    public void mouseClicked(MouseEvent pMouseEvent) {
        // Minimap click → jump viewport
        if (viewportEnabled && minimapEnabled && handleMinimapClick(pMouseEvent)) {
            return;
        }

        MouseEvent translated = translateMouseEvent(pMouseEvent);

        Optional<Overlay> overlay = getClient().getActiveOverlay();
        if (!pMouseEvent.isShiftDown() && overlay.isPresent()) {
            overlay.get().mouseClicked(translated);
            return;
        }

        ClientState<? extends LogicModule, ? extends FantasyFootballClient> uiState = getClient().getClientState();
        if (uiState != null) {
            uiState.mouseClicked(translated);
        }
    }

    // MouseListener
    public void mouseEntered(MouseEvent pMouseEvent) {
        MouseEvent translated = translateMouseEvent(pMouseEvent);

        Optional<Overlay> overlay = getClient().getActiveOverlay();
        if (overlay.isPresent()) {
            overlay.get().mouseEntered(translated);
            return;
        }

        ClientState<? extends LogicModule, ? extends FantasyFootballClient> uiState = getClient().getClientState();
        if (uiState != null) {
            uiState.mouseEntered(translated);
        }
    }

    // MouseListener
    public void mouseExited(MouseEvent pMouseEvent) {
        MouseEvent translated = translateMouseEvent(pMouseEvent);

        Optional<Overlay> overlay = getClient().getActiveOverlay();
        if (overlay.isPresent()) {
            overlay.get().mouseExited(translated);
            return;
        }

        ClientState<? extends LogicModule, ? extends FantasyFootballClient> uiState = getClient().getClientState();
        if (uiState != null) {
            uiState.mouseExited(translated);
        }
    }

    // MouseListener
    public void mousePressed(MouseEvent pMouseEvent) {
        // Start panning with middle mouse button
        if (viewportEnabled && javax.swing.SwingUtilities.isMiddleMouseButton(pMouseEvent)) {
            panDragStart = pMouseEvent.getPoint();
            panStartVpX = viewportX;
            panStartVpY = viewportY;
            return; // consume
        }

        MouseEvent translated = translateMouseEvent(pMouseEvent);

        Optional<Overlay> overlay = getClient().getActiveOverlay();
        if (!pMouseEvent.isShiftDown() && overlay.isPresent()) {
            overlay.get().mousePressed(translated);
            return;
        }

        ClientState<? extends LogicModule, ? extends FantasyFootballClient> uiState = getClient().getClientState();
        if (uiState != null) {
            uiState.mousePressed(translated);
        }
    }

    // MouseListener
    public void mouseReleased(MouseEvent pMouseEvent) {
        // End panning
        if (panDragStart != null) {
            panDragStart = null;
            return; // consume
        }

        MouseEvent translated = translateMouseEvent(pMouseEvent);

        Optional<Overlay> overlay = getClient().getActiveOverlay();
        if (!pMouseEvent.isShiftDown() && overlay.isPresent()) {
            overlay.get().mouseReleased(translated);
            return;
        }

        ClientState<? extends LogicModule, ? extends FantasyFootballClient> uiState = getClient().getClientState();
        if (uiState != null) {
            uiState.mouseReleased(translated);
        }
    }

    // MouseWheelListener — NEW
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (!viewportEnabled) return;

        int scrollAmount = e.getWheelRotation() * pitchDimensionProvider.fieldSquareSize();

        if (e.isShiftDown()) {
            // Shift + scroll = horizontal panning
            setViewportPosition(viewportX + scrollAmount, viewportY);
        } else {
            // Plain scroll = vertical panning
            setViewportPosition(viewportX, viewportY + scrollAmount);
        }

        repaint();
    }

    /**
     * Handle clicks on the minimap — jump viewport to clicked location.
     * @return true if the click was on the minimap and was handled
     */
    private boolean handleMinimapClick(MouseEvent e) {
        if (fImage == null) return false;

        int mmWidth = (int) (getWidth() * MINIMAP_SCALE);
        int mmHeight = (int) (mmWidth * ((double) fImage.getHeight() / fImage.getWidth()));
        int mmX = getWidth() - mmWidth - MINIMAP_MARGIN;
        int mmY = getHeight() - mmHeight - MINIMAP_MARGIN;

        // Check if click is within minimap bounds
        if (e.getX() >= mmX && e.getX() <= mmX + mmWidth
                && e.getY() >= mmY && e.getY() <= mmY + mmHeight) {

            // Convert minimap click to fImage coordinates
            double scaleX = (double) fImage.getWidth() / mmWidth;
            double scaleY = (double) fImage.getHeight() / mmHeight;
            int fieldPixelX = (int) ((e.getX() - mmX) * scaleX);
            int fieldPixelY = (int) ((e.getY() - mmY) * scaleY);

            // Center viewport on clicked point
            setViewportPosition(fieldPixelX - viewportWidth / 2,
                    fieldPixelY - viewportHeight / 2);
            repaint();
            return true;
        }
        return false;
    }

    // ====================== ACCESSORS ======================

    public FantasyFootballClient getClient() {
        return fClient;
    }

    public BufferedImage getImage() {
        return fImage;
    }

    public int getViewportX() {
        return viewportX;
    }

    public int getViewportY() {
        return viewportY;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }
}