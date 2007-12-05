/*
 * $Id: RSSChannelRefresh.java 59 2007-12-02 03:41:15Z jasta00 $
 *
 * Copyright (C) 2007 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * TODO: This class needs to be generalized much better, with specialized
 * parsers for Atom 1.0, Atom 0.3, RSS 0.91, RSS 1.0 and RSS 2.0.  Hell,
 * this whole thing needs to be chucked and redone.
 * 
 * Date parser code lifted from Informa <http://informa.sourceforge.net>.
 */

package org.devtcg.rssreader.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.devtcg.rssreader.provider.RSSReader;
import org.xml.sax.XMLReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.ContentURI;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class ChannelRefresh extends DefaultHandler
{
	private static final String TAG = "RSSChannelRefresh";

	private Handler mHandler;
	private long mID;
	private String mRSSURL;
	
	private ContentResolver mContent;
	
	/* Buffer post information as we learn it in STATE_IN_ITEM. */
	private RSSChannelPost mPostBuf;
	
	/* Efficiency is the name of the game here... */
	private int mState;
	private static final int STATE_IN_ITEM = (1 << 2);
	private static final int STATE_IN_ITEM_TITLE = (1 << 3);
	private static final int STATE_IN_ITEM_LINK = (1 << 4);
	private static final int STATE_IN_ITEM_DESC = (1 << 5);
	private static final int STATE_IN_ITEM_DATE = (1 << 6);
	private static final int STATE_IN_ITEM_AUTHOR = (1 << 7);
	private static final int STATE_IN_TITLE = (1 << 8);
	
	private static HashMap<String, Integer> mStateMap;
	
	private static final SimpleDateFormat[] dateFormats;
	private static final int dateFormat_default;
	
	static
	{
		mStateMap = new HashMap<String, Integer>();		
		mStateMap.put("item", new Integer(STATE_IN_ITEM));
		mStateMap.put("entry", new Integer(STATE_IN_ITEM));
		mStateMap.put("title", new Integer(STATE_IN_ITEM_TITLE));
		mStateMap.put("link", new Integer(STATE_IN_ITEM_LINK));
		mStateMap.put("description", new Integer(STATE_IN_ITEM_DESC));
		mStateMap.put("content", new Integer(STATE_IN_ITEM_DESC));
		mStateMap.put("dc:date", new Integer(STATE_IN_ITEM_DATE));
		mStateMap.put("updated", new Integer(STATE_IN_ITEM_DATE));
		mStateMap.put("pubDate", new Integer(STATE_IN_ITEM_DATE));
		mStateMap.put("dc:author", new Integer(STATE_IN_ITEM_AUTHOR));
		mStateMap.put("author", new Integer(STATE_IN_ITEM_AUTHOR));
		
		dateFormat_default = 6;
	    final String[] possibleDateFormats =
	    {
	    	"EEE, dd MMM yyyy HH:mm:ss z", // RFC_822
	    	"EEE, dd MMM yyyy HH:mm zzzz",
	    	"yyyy-MM-dd'T'HH:mm:ssZ",
	    	"yyyy-MM-dd'T'HH:mm:ss.SSSzzzz", // Blogger Atom feed has millisecs also
	    	"yyyy-MM-dd'T'HH:mm:sszzzz",
	    	"yyyy-MM-dd'T'HH:mm:ss z",
	    	"yyyy-MM-dd'T'HH:mm:ssz", // ISO_8601
	    	"yyyy-MM-dd'T'HH:mm:ss",
	    	"yyyy-MM-dd'T'HHmmss.SSSz",
	    	"yyyy-MM-dd"
	    };

	    dateFormats = new SimpleDateFormat[possibleDateFormats.length];
	    TimeZone gmtTZ = TimeZone.getTimeZone("GMT");
	    
	    for (int i = 0; i < possibleDateFormats.length; i++)
	    {
	    	/* TODO: Support other locales? */
	    	dateFormats[i] = new SimpleDateFormat(possibleDateFormats[i],
	    	  Locale.ENGLISH);
	    	
	    	dateFormats[i].setTimeZone(gmtTZ);
	    }
	}

	public ChannelRefresh(ContentResolver resolver)
	{
		super();
		mContent = resolver;
	}

	/*
	 * Note that if syncDB is called with id == -1, this class will interpret
	 * that to mean that a new channel is being added (and also tested) so
	 * the first meaningful piece of data encountered will trigger an insert
	 * into the database.
	 * 
	 * This logic is all just terrible, but this entire class needs to be
	 * scrapped and redone to make room for improved cooperation with the rest
	 * of the application.
	 */
	public long syncDB(Handler h, long id, String rssurl)
	  throws Exception
	{
		mHandler = h;
		mID = id;
		mRSSURL = rssurl;

		SAXParserFactory spf = SAXParserFactory.newInstance();
		SAXParser sp = spf.newSAXParser();
		XMLReader xr = sp.getXMLReader();

		xr.setContentHandler(this);
		xr.setErrorHandler(this);

		URL url = new URL(mRSSURL);
		xr.parse(new InputSource(url.openStream()));

		return mID;
	}

	public boolean updateFavicon(long id, String rssurl)
	{
		InputStream stream = null;
		OutputStream ico = null;
		
		boolean r = false;
		
		try
		{
			URL orig = new URL(rssurl);

			URL iconUrl = new URL(orig.getProtocol(), orig.getHost(),
			  orig.getPort(), "/favicon.ico");

			stream = iconUrl.openStream();
			
			ico =
			  mContent.openOutputStream(RSSReader.Channels.CONTENT_URI.addId(id).addPath("icon"));
			
			byte[] b = new byte[1024];

			int n;
			while ((n = stream.read(b)) != -1)
				ico.write(b, 0, n);
			
			r = true;
		}
		catch (Exception e)
		{
			Log.d(TAG, Log.getStackTraceString(e));
		}
		finally
		{
			try
			{
				if (stream != null)
					stream.close();

				if (ico != null)
					ico.close();
			}
			catch (IOException e) { }
		}
		
		return r;
	}

	public void startElement(String uri, String name, String qName,
			Attributes attrs)
	{
		/* HACK: when we see <title> outside of an <item>, assume it's the
		 * feed title.  Only do this when we are inserting a new feed. */
		if (mID == -1 && 
		    qName.equals("title") && (mState & STATE_IN_ITEM) == 0)
		{
			mState |= STATE_IN_TITLE;
			return;
		}
		
		Integer state = mStateMap.get(qName);

		if (state != null)
		{
			mState |= state.intValue();

			if (state.intValue() == STATE_IN_ITEM)
				mPostBuf = new RSSChannelPost();
			else if ((mState & STATE_IN_ITEM) != 0 && state.intValue() == STATE_IN_ITEM_LINK)
			{
				String href = attrs.getValue("href");
				
				if (href != null)
					mPostBuf.link = href;
			}
		}
	}

	public void endElement(String uri, String name, String qName)
	{
		Integer state = mStateMap.get(qName);

		if (state != null)
		{
			mState &= ~(state.intValue());

			if (state.intValue() == STATE_IN_ITEM)
			{
				if (mID == -1)
				{
					Log.d(TAG, "Oops, </item> found before feed title and our parser sucks too much to deal.");
					return;
				}
				
				String[] dupProj = 
				  new String[] { RSSReader.Posts._ID };

				ContentURI listURI =
				  RSSReader.Posts.CONTENT_URI_LIST.addId(new Long(mID).longValue());

				Cursor dup = mContent.query(listURI,
					dupProj, "title = ? AND url = ?",
					new String[] { mPostBuf.title, mPostBuf.link}, null);

				Log.d(TAG, "Post: " + mPostBuf.title);

				if (dup.count() == 0)
				{
					ContentValues values = new ContentValues();

					values.put(RSSReader.Posts.CHANNEL_ID, mID);
					values.put(RSSReader.Posts.TITLE, mPostBuf.title);
					values.put(RSSReader.Posts.URL, mPostBuf.link);
					values.put(RSSReader.Posts.AUTHOR, mPostBuf.author);
					values.put(RSSReader.Posts.DATE, mPostBuf.getDate());
					values.put(RSSReader.Posts.BODY, mPostBuf.desc);
					
					mContent.insert(RSSReader.Posts.CONTENT_URI, values);
				}
			}
		}
	}

	public void characters(char ch[], int start, int length)
	{
		/* HACK: This is the other side of the above hack in startElement. */
		if (mID == -1 && (mState & STATE_IN_TITLE) != 0)
		{
			ContentValues values = new ContentValues();
			
			values.put(RSSReader.Channels.TITLE, new String(ch, start, length));
			values.put(RSSReader.Channels.URL, mRSSURL);
			
			ContentURI added =
			  mContent.insert(RSSReader.Channels.CONTENT_URI, values);
			
			mID = new Long(added.getPathSegment(1));
			
			/* There's no reason we need to do this ever, but we'll just be
			 * good about removing this awful hack from runtime data. */
			mState &= ~STATE_IN_TITLE;
			
			return;
		}
		
		if ((mState & STATE_IN_ITEM) == 0)
			return;
		
		/* 
		 * We sort of pretended that mState was inclusive, but really only
		 * STATE_IN_ITEM is inclusive here.  This is a goofy design, but it is
		 * done to make this code a bit simpler and more efficient.
		 */
		switch (mState)
		{
		case STATE_IN_ITEM | STATE_IN_ITEM_TITLE:
			mPostBuf.title = new String(ch, start, length);
			break;
		case STATE_IN_ITEM | STATE_IN_ITEM_DESC:
			mPostBuf.desc = new String(ch, start, length);
			break;
		case STATE_IN_ITEM | STATE_IN_ITEM_LINK:
			mPostBuf.link = new String(ch, start, length);
			break;
		case STATE_IN_ITEM | STATE_IN_ITEM_DATE:
			mPostBuf.setDate(new String(ch, start, length));
			break;
		case STATE_IN_ITEM | STATE_IN_ITEM_AUTHOR:
			mPostBuf.author = new String(ch, start, length);
			break;
		default:
			/* Don't care... */
		}
	}
	
	/* Copied verbatim from Informa 0.7.0-alpha2 ParserUtils.java. */
	private static Date parseDate(String strdate) {
		Date result = null;
		strdate = strdate.trim();
		if (strdate.length() > 10) {

			// TODO deal with +4:00 (no zero before hour)
			if ((strdate.substring(strdate.length() - 5).indexOf("+") == 0 || strdate
					.substring(strdate.length() - 5).indexOf("-") == 0)
					&& strdate.substring(strdate.length() - 5).indexOf(":") == 2) {

				String sign = strdate.substring(strdate.length() - 5,
						strdate.length() - 4);

				strdate = strdate.substring(0, strdate.length() - 5) + sign + "0"
				+ strdate.substring(strdate.length() - 4);
				// logger.debug("CASE1 : new date " + strdate + " ? "
				//    + strdate.substring(0, strdate.length() - 5));

			}

			String dateEnd = strdate.substring(strdate.length() - 6);

			// try to deal with -05:00 or +02:00 at end of date
			// replace with -0500 or +0200
			if ((dateEnd.indexOf("-") == 0 || dateEnd.indexOf("+") == 0)
					&& dateEnd.indexOf(":") == 3) {
				// TODO deal with GMT-00:03
				if ("GMT".equals(strdate.substring(strdate.length() - 9, strdate
						.length() - 6))) {
					Log.d(TAG, "General time zone with offset, no change");
				} else {
					// continue treatment
					String oldDate = strdate;
					String newEnd = dateEnd.substring(0, 3) + dateEnd.substring(4);
					strdate = oldDate.substring(0, oldDate.length() - 6) + newEnd;
					// logger.debug("!!modifying string ->"+strdate);
				}
			}
		}
		int i = 0;
		while (i < dateFormats.length) {
			try {
				result = dateFormats[i].parse(strdate);
				// logger.debug("******Parsing Success "+strdate+"->"+result+" with
				// "+dateFormats[i].toPattern());
				break;
			} catch (java.text.ParseException eA) {
				i++;
			}
		}

		return result;
	}
	
	private class RSSChannelPost
	{
		public String title;
		public Date date;
		public String desc;
		public String link;
		public String author;
		
		public RSSChannelPost()
		{
			/* Empty. */
		}
		
		public void setDate(String str)
		{
			date = parseDate(str);
			
			if (date == null)
				date = new Date();
		}
		
		public String getDate()
		{
			return dateFormats[dateFormat_default].format(mPostBuf.date);
		}
	}
}
