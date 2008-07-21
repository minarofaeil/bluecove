/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2007-2008 Vlad Skarzhevskyy
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  @version $Id$
 */
package com.intel.bluetooth.obex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.obex.HeaderSet;
import javax.obex.ResponseCodes;

import com.intel.bluetooth.DebugLog;

/**
 * @author vlads
 * 
 */
class OBEXServerOperationGet extends OBEXServerOperation implements OBEXOperationDelivery, OBEXOperationReceive {

	protected OBEXServerOperationGet(OBEXServerSessionImpl session, HeaderSet receivedHeaders, boolean finalPacket)
			throws IOException {
		super(session, receivedHeaders);
		if (finalPacket) {
			requestEnded = true;
		}
		this.inputStream = new OBEXOperationInputStream(this);
		processIncommingData(receivedHeaders, finalPacket);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.microedition.io.InputConnection#openInputStream()
	 */
	public InputStream openInputStream() throws IOException {
		if (isClosed) {
			throw new IOException("operation closed");
		}
		if (inputStreamOpened) {
			throw new IOException("input stream already open");
		}
		this.inputStreamOpened = true;
		return this.inputStream;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.microedition.io.OutputConnection#openOutputStream()
	 */
	public OutputStream openOutputStream() throws IOException {
		if (isClosed) {
			throw new IOException("operation closed");
		}
		if (outputStream != null) {
			throw new IOException("output stream already open");
		}
		requestEnded = true;
		outputStream = new OBEXOperationOutputStream(session.mtu, this);
		session.writeOperation(OBEXOperationCodes.OBEX_RESPONSE_CONTINUE, OBEXHeaderSetImpl.toByteArray(sendHeaders));
		sendHeaders = null;
		return outputStream;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.microedition.io.Connection#close()
	 */
	public void close() throws IOException {
		if (outputStream != null) {
			outputStream.close();
			outputStream = null;
		}
		inputStream.close();
		super.close();
	}

	protected boolean readRequestPacket() throws IOException {
		byte[] b = session.readOperation();
		int opcode = b[0] & 0xFF;
		boolean finalPacket = ((opcode & OBEXOperationCodes.FINAL_BIT) != 0);
		if (finalPacket) {
			DebugLog.debug("server operation got final packet");
			finalPacketReceived = true;
		}
		switch (opcode) {
		case OBEXOperationCodes.GET_FINAL:
		case OBEXOperationCodes.GET:
			if (finalPacket) {
				requestEnded = true;
			}
			HeaderSet requestHeaders = OBEXHeaderSetImpl.readHeaders(b[0], b, 3);
			OBEXHeaderSetImpl.appendHeaders(this.receivedHeaders, requestHeaders);
			processIncommingData(requestHeaders, finalPacket);
			break;
		case OBEXOperationCodes.ABORT:
			processAbort();
			break;
		default:
			errorReceived = true;
			DebugLog.debug0x("server operation invalid request", OBEXUtils.toStringObexResponseCodes(opcode), opcode);
			session.writeOperation(ResponseCodes.OBEX_HTTP_BAD_REQUEST, null);
		}
		return finalPacket;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.obex.OBEXOperationReceive#receiveData(com.intel.bluetooth.obex.OBEXOperationInputStream)
	 */
	public void receiveData(OBEXOperationInputStream is) throws IOException {
		if (requestEnded || errorReceived) {
			this.inputStream.appendData(null, true);
			return;
		}
		DebugLog.debug("server operation reply continue");
		session.writeOperation(OBEXOperationCodes.OBEX_RESPONSE_CONTINUE, OBEXHeaderSetImpl.toByteArray(sendHeaders));
		sendHeaders = null;
		readRequestPacket();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.intel.bluetooth.obex.OBEXOperationDelivery#deliverPacket(boolean,
	 *      byte[])
	 */
	public void deliverPacket(boolean finalPacket, byte[] buffer) throws IOException {
		HeaderSet dataHeaders = OBEXSessionBase.createOBEXHeaderSet();
		int opcode = OBEXOperationCodes.OBEX_RESPONSE_CONTINUE;
		int dataHeaderID = OBEXHeaderSetImpl.OBEX_HDR_BODY;
		if (finalPacket) {
			// opcode = OBEXOperationCodes.OBEX_RESPONSE_SUCCESS;
			dataHeaderID = OBEXHeaderSetImpl.OBEX_HDR_BODY_END;
		}
		dataHeaders.setHeader(dataHeaderID, buffer);
		if (sendHeaders != null) {
			OBEXHeaderSetImpl.appendHeaders(dataHeaders, sendHeaders);
			sendHeaders = null;
		}
		session.writeOperation(opcode, OBEXHeaderSetImpl.toByteArray(dataHeaders));
		readRequestPacket();
	}

	private void processAbort() throws IOException {
		// TODO proper abort + UnitTests
		finalPacketReceived = true;
		requestEnded = true;
		session.writeOperation(OBEXOperationCodes.OBEX_RESPONSE_SUCCESS, null);
		throw new IOException("Operation aborted");
	}

}
