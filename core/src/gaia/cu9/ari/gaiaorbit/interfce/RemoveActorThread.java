/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaia.cu9.ari.gaiaorbit.interfce;

import com.badlogic.gdx.scenes.scene2d.Actor;

public class RemoveActorThread extends Thread {
    private Actor actor;
    private float timeSeconds;

    public RemoveActorThread(Actor actor, float seconds) {
        this.actor = actor;
        this.timeSeconds = seconds;
        this.setDaemon(true);
    }

    public void run() {
        try {
            Thread.sleep(Math.round(timeSeconds * 1000));
        } catch (InterruptedException e) {
        }
        // Remove actor
        actor.remove();
    }
}

