/*******************************************************************************
 * Copyright (c) 2017, 2019 Sebastian Palarus
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Sebastian Palarus - initial API and implementation
 *******************************************************************************/
package org.sodeac.streampartitioner.example.api;

public interface Events
{
    String PROPERTY_TCP_PORT = "TCP_PORT";
    String PROPERTY_KEYSPEC = "KEYSPEC";
    String TOPIC_BASE = "org/sodeac/streampartitioner/example/";
    String TOPIC_REQUEST_START_SERVER = TOPIC_BASE + "request/startserver";
    String TOPIC_REQUEST_STOP_SERVER = TOPIC_BASE + "request/stoptserver";
    String TOPIC_REQUEST_NOTIFY_SERVER_STATE = TOPIC_BASE + "request/notifyserverstate";
    String TOPIC_NOTIFY_START_SERVER = TOPIC_BASE + "notify/startserver";
    String TOPIC_NOTIFY_STOP_SERVER = TOPIC_BASE + "notify/stoptserver";
}
