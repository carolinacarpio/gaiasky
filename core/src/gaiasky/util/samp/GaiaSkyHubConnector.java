/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.samp;

import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.i18n.I18n;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.SampException;

public class GaiaSkyHubConnector extends HubConnector {
    private static final Log logger = Logger.getLogger(GaiaSkyHubConnector.class);

    public GaiaSkyHubConnector(ClientProfile profile) {
        super(profile);
    }

    @Override
    protected void connectionChanged(boolean isConnected) {
        super.connectionChanged(isConnected);
        String hubName = "-";
        try {
            hubName = super.getConnection().getRegInfo().getHubId();
        } catch (NullPointerException | SampException ignored) {
        }

        logger.info(isConnected ? I18n.msg("samp.connected", hubName) : I18n.msg("samp.disconnected"));
        EventManager.publish(Event.POST_POPUP_NOTIFICATION, this, isConnected ? I18n.msg("samp.connected", hubName) : I18n.msg("samp.disconnected"));
    }

    @Override
    protected void disconnect() {
        super.disconnect();
        logger.info(I18n.msg("samp.disconnected"));
    }

}
