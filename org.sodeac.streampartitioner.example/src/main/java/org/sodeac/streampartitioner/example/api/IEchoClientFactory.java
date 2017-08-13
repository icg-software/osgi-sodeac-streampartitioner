package org.sodeac.streampartitioner.example.api;

public interface IEchoClientFactory
{
	public IEchoClient createEchoClient() throws ServerNotRunningException;
}
