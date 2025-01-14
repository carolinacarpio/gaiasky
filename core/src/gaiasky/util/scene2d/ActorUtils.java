
/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.scene2d;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;

public class ActorUtils {
    /**
     * Makes sure that the actor will be fully visible in the stage. If necessary, the actor position will be changed to fit
     * on screen.
     *
     * @throws IllegalStateException if the actor is not in a stage.
     */
    public static void keepWithinStage(Actor actor) {
        Stage stage = actor.getStage();
        if (stage == null) {
            throw new IllegalStateException("The actor is not in a stage: " + actor.getName());
        }
        keepWithinStage(actor.getStage(), actor);
    }

    /**
     * Ensures the actor is fully visible in stage.
     */
    public static void keepWithinStage(Stage stage, Actor actor) {
        Camera camera = stage.getCamera();
        if (camera instanceof OrthographicCamera) {
            OrthographicCamera orthographicCamera = (OrthographicCamera) camera;
            float parentWidth = stage.getWidth();
            float parentHeight = stage.getHeight();
            if (actor.getX(Align.right) - camera.position.x > parentWidth / 2 / orthographicCamera.zoom)
                actor.setPosition(camera.position.x + parentWidth / 2 / orthographicCamera.zoom, actor.getY(Align.right), Align.right);
            if (actor.getX(Align.left) - camera.position.x < -parentWidth / 2 / orthographicCamera.zoom)
                actor.setPosition(camera.position.x - parentWidth / 2 / orthographicCamera.zoom, actor.getY(Align.left), Align.left);
            if (actor.getY(Align.top) - camera.position.y > parentHeight / 2 / orthographicCamera.zoom)
                actor.setPosition(actor.getX(Align.top), camera.position.y + parentHeight / 2 / orthographicCamera.zoom, Align.top);
            if (actor.getY(Align.bottom) - camera.position.y < -parentHeight / 2 / orthographicCamera.zoom)
                actor.setPosition(actor.getX(Align.bottom), camera.position.y - parentHeight / 2 / orthographicCamera.zoom, Align.bottom);
        } else if (actor.getParent() == stage.getRoot()) {
            float parentWidth = stage.getWidth();
            float parentHeight = stage.getHeight();
            if (actor.getX() < 0)
                actor.setX(0);
            if (actor.getRight() > parentWidth)
                actor.setX(parentWidth - actor.getWidth());
            if (actor.getY() < 0)
                actor.setY(0);
            if (actor.getTop() > parentHeight)
                actor.setY(parentHeight - actor.getHeight());
        }
    }
}
