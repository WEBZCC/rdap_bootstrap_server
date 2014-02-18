/*
 * Copyright (C) 2013, 2014 American Registry for Internet Numbers (ARIN)
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 */
package net.arin.rdap_bootstrap.service;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.googlecode.ipv6.IPv6Address;
import com.googlecode.ipv6.IPv6Network;
import net.arin.rdap_bootstrap.json.Notice;
import net.arin.rdap_bootstrap.json.Response;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @version $Rev$, $Date$
 */
public class RedirectServlet extends HttpServlet
{
    private AsAllocations asAllocations = new AsAllocations();
    private IpV6Allocations ipV6Allocations = new IpV6Allocations();
    private IpV4Allocations ipV4Allocations = new IpV4Allocations();
    private TldAllocations tldAllocations = new TldAllocations();

    private volatile Statistics statistics;

    private ResourceFiles resourceFiles;
    private Timer timer;

    private static final long CHECK_CONFIG_FILES = 60000L;

    @Override
    public void init( ServletConfig config ) throws ServletException
    {
        try
        {
            LoadConfigTask loadConfigTask = new LoadConfigTask();
            loadConfigTask.loadStats();

            if( config != null )
            {
                timer = new Timer(  );
                timer.schedule( loadConfigTask, CHECK_CONFIG_FILES, CHECK_CONFIG_FILES );
            }
        }
        catch ( Exception e )
        {
            throw new ServletException( e );
        }
    }

    @Override
    protected void service( HttpServletRequest req, HttpServletResponse resp )
        throws ServletException, IOException
    {
        String pathInfo = req.getPathInfo();
        if( pathInfo.startsWith( "/domain/" ) )
        {
            try
            {
                String base = makeDomainBase( pathInfo );
                if( base == null )
                {
                    resp.sendError( HttpServletResponse.SC_NOT_FOUND );
                }
                else
                {
                    String url = makeRedirectUrl( req, base );
                    resp.sendRedirect( url );
                }
            }
            catch ( Exception e )
            {
                resp.sendError( HttpServletResponse.SC_BAD_REQUEST, e.getMessage() );
            }
        }
        else if( pathInfo.startsWith( "/nameserver/" ) )
        {
            try
            {
                String base = makeNameserverBase( pathInfo );
                if( base == null )
                {
                    resp.sendError( HttpServletResponse.SC_NOT_FOUND );
                }
                else
                {
                    String url = makeRedirectUrl( req, base );
                    resp.sendRedirect( url );
                }
            }
            catch ( Exception e )
            {
                resp.sendError( HttpServletResponse.SC_BAD_REQUEST, e.getMessage() );
            }
        }
        else if( pathInfo.startsWith( "/ip/" ) )
        {
            try
            {
                String base = makeIpBase( pathInfo );
                if( base == null )
                {
                    resp.sendError( HttpServletResponse.SC_NOT_FOUND );
                }
                else
                {
                    String url = makeRedirectUrl( req, base );
                    resp.sendRedirect( url );
                }
            }
            catch ( Exception e )
            {
                resp.sendError( HttpServletResponse.SC_BAD_REQUEST, e.getMessage() );
            }
        }
        else if( pathInfo.startsWith( "/entity/" ) )
        {
            try
            {
                String base = makeEntityBase( pathInfo );
                if( base == null )
                {
                    resp.sendError( HttpServletResponse.SC_NOT_FOUND );
                }
                else
                {
                    String url = makeRedirectUrl( req, base );
                    resp.sendRedirect( url );
                }
            }
            catch ( Exception e )
            {
                resp.sendError( HttpServletResponse.SC_BAD_REQUEST, e.getMessage() );
            }
        }
        else if( pathInfo.startsWith( "/autnum/" ) )
        {
            try
            {
                long autnum = makeAutNumLong( pathInfo );
                String base = asAllocations.getUrl( autnum, statistics.getAsHitCounter() );
                if( base == null )
                {
                    resp.sendError( HttpServletResponse.SC_NOT_FOUND );
                }
                else
                {
                    String url = makeRedirectUrl( req, base );
                    resp.sendRedirect( url );
                }
            }
            catch ( Exception e )
            {
                resp.sendError( HttpServletResponse.SC_BAD_REQUEST, e.getMessage() );
            }
        }
        else if( pathInfo.startsWith( "/help" ) )
        {
            resp.setContentType( "application/rdap+json" );
            makeHelp( resp.getOutputStream() );
        }
        else
        {
            resp.sendError( HttpServletResponse.SC_NOT_FOUND );
        }
    }

    private String makeRedirectUrl( HttpServletRequest req, String base )
    {
        return req.getScheme() + base + req.getPathInfo();
    }

    public long makeAutNumLong( String pathInfo )
    {
        long autnum = Long.parseLong( pathInfo.split( "/" )[2] );
        return autnum;
    }

    public String makeIpBase( String pathInfo )
    {
        //strip leading "/ip/"
        pathInfo = pathInfo.substring( 4 );
        if( pathInfo.indexOf( ":" ) == -1 ) //is not ipv6
        {
            String firstOctet = pathInfo.split( "\\." )[ 0 ];
            return ipV4Allocations.getUrl( Integer.parseInt( firstOctet ), statistics.getIp4RirHitCounter() );
        }
        //else
        IPv6Address addr = null;
        if( pathInfo.indexOf( "/" ) == -1 )
        {
            addr = IPv6Address.fromString( pathInfo );
        }
        else
        {
            IPv6Network net = IPv6Network.fromString( pathInfo );
            addr = net.getFirst();
        }
        return ipV6Allocations.getUrl( addr, statistics.getIp6RirHitCounter() );
    }

    public String makeDomainBase( String pathInfo )
    {
        //strip leading "/domain/"
        pathInfo = pathInfo.substring( 8 );
        //strip possible trailing period
        if( pathInfo.endsWith( "." ) )
        {
            pathInfo = pathInfo.substring( 0, pathInfo.length() - 1 );
        }
        if( pathInfo.endsWith( ".in-addr.arpa" ) )
        {
            String[] labels = pathInfo.split( "\\." );
            String firstOctet = labels[ labels.length -3 ];
            return ipV4Allocations.getUrl( Integer.parseInt( firstOctet ), statistics.getDomainRirHitCounter() );
        }
        else if( pathInfo.endsWith( ".ip6.arpa" ) )
        {
            String[] labels = pathInfo.split( "\\." );
            byte[] bytes = new byte[ 16 ];
            Arrays.fill( bytes, ( byte ) 0 );
            int labelIdx = labels.length -3;
            int byteIdx = 0;
            int idxJump = 1;
            while( labelIdx > 0 )
            {
                char ch = labels[ labelIdx ].charAt( 0 );
                byte value = 0;
                if( ch >= '0' && ch <= '9' )
                {
                    value = (byte)(ch - '0');
                }
                else if (ch >= 'A' && ch <= 'F' )
                {
                    value = (byte)(ch - ( 'A' - 0xaL ) );
                }
                else if (ch >= 'a' && ch <= 'f' )
                {
                    value = (byte)(ch - ( 'a' - 0xaL ) );
                }
                if( idxJump % 2 == 1 )
                {
                    bytes[ byteIdx ] = ( byte ) (value << 4);
                }
                else
                {
                    bytes[ byteIdx ] = ( byte ) (bytes[ byteIdx ] + value);
                }
                labelIdx--;
                idxJump++;
                if( idxJump % 2 == 1 )
                {
                    byteIdx++;
                }
            }
            return ipV6Allocations.getUrl( IPv6Address.fromByteArray( bytes ), statistics.getDomainRirHitCounter() );
        }
        //else
        String[] labels = pathInfo.split( "\\." );
        return tldAllocations.getUrl( labels[ labels.length -1 ], statistics.getDomainTldHitCounter() );
    }

    public String makeNameserverBase( String pathInfo )
    {
        //strip leading "/nameserver/"
        pathInfo = pathInfo.substring( 12 );
        //strip possible trailing period
        if( pathInfo.endsWith( "." ) )
        {
            pathInfo = pathInfo.substring( 0, pathInfo.length() - 1 );
        }
        String[] labels = pathInfo.split( "\\." );
        return tldAllocations.getUrl( labels[ labels.length -1 ], statistics.getNsTldHitCounter() );
    }

    public String makeEntityBase( String pathInfo )
    {
        String retval = null;
        //try the RIRs first
        if( pathInfo.endsWith( "-ARIN" ) )
        {
           retval = statistics.getRirMap().getRirUrl( "ARIN" );
        }
        else if( pathInfo.endsWith( "-AP" ) )
        {
            retval = statistics.getRirMap().getRirUrl( "APNIC" );
        }
        else if( pathInfo.endsWith( "-RIPE" ) )
        {
            retval = statistics.getRirMap().getRirUrl( "RIPE" );
        }
        else if( pathInfo.endsWith( "-LACNIC" ) )
        {
            retval = statistics.getRirMap().getRirUrl( "LACNIC" );
        }
        else if( pathInfo.endsWith( "-AFRINIC" ) )
        {
            retval = statistics.getRirMap().getRirUrl( "AFRINIC" );
        }
        if( retval != null )
        {
            HitCounter hitCounter = statistics.getEntityRirHitCounter();
            hitCounter.incrementCounter( retval );
            return retval;
        }
        //else try the TLDs
        int i = pathInfo.lastIndexOf( "-" );
        if( i != -1 && i != pathInfo.length() - 1 )
        {
            String tld = pathInfo.substring( i+1 );
            retval = tldAllocations.getUrl( tld );
            HitCounter hitCounter = statistics.getEntityTldHitCounter();
            hitCounter.incrementCounter( retval );
        }
        else
        {
            HitCounter hitCounter = statistics.getEntityTldHitCounter();
            hitCounter.incrementCounter( "" ); //record a miss
        }
        return retval;
    }

    private Notice makeStatsNotice( String title, HashMap<String, AtomicLong> hashMap )
    {
        Notice notice = new Notice();
        notice.setTitle( title );
        String[] description = new String[ hashMap.size() ];
        int i = 0;
        for ( Entry<String, AtomicLong> entry : hashMap.entrySet() )
        {
            description[ i++ ] = String.format( "%-25s = %5d", entry.getKey(), entry.getValue().get() );
        }
        notice.setDescription( description );
        return notice;
    }

    private Notice makeMappings()
    {
        Notice notice = new Notice();
        notice.setTitle( "Redirection Mappings" );
        ArrayList<String> description = new ArrayList<String>();
        for (Entry<Object, Object> entry : statistics.getRirMap().getPropertyMappings().entrySet())
        {
            String s = String.format( "%-8s -> http(s)%s", entry.getKey(), entry.getValue() );
            description.add( s );
        }
        for (Entry<String, String> entry : tldAllocations.getAllocationMappings().entrySet())
        {
            String s = String.format( "%-8s -> http(s)%s", entry.getKey(), entry.getValue() );
            description.add( s );
        }
        notice.setDescription( description.toArray( new String[ description.size() ] ) );
        return notice;
    }

    public void makeHelp( OutputStream outputStream ) throws IOException
    {
        Response response = new Response( null );
        ArrayList<Notice> notices = new ArrayList<Notice>();

        notices.add( makeMappings() );

        //do statistics
        notices.add(makeStatsNotice("Autnum hits by RIR", statistics.getAsRirHits()));
        notices.add(makeStatsNotice("IPv4 hits by RIR", statistics.getIp4RirHits()));
        notices.add(makeStatsNotice("IPv6s hits by RIR", statistics.getIp6RirHits()));
        notices.add(makeStatsNotice("Entity hits by RIR", statistics.getEntityRirHits()));
        notices.add(makeStatsNotice("Entity hits by TLD", statistics.getEntityTldHits()));
        notices.add(makeStatsNotice("Domain hits by RIR", statistics.getDomainRirHits()));
        notices.add(makeStatsNotice("Domain hits by TLD", statistics.getDomainTldHits()));
        notices.add( makeStatsNotice( "NS hits by TLD", statistics.getNsTldHits() ) );

        //totals
        Notice notice = new Notice();
        notice.setTitle( "Totals" );
        String[] description = new String[ 2 ];
        description[ 0 ] = String.format( "Hits   = %5d", statistics.getTotalHits().get() );
        description[ 1 ] = String.format( "Misses = %5d", statistics.getTotalMisses().get() );
        notice.setDescription( description );
        notices.add( notice );

        response.setNotices( notices );

        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer( new DefaultPrettyPrinter(  ) );
        writer.writeValue( outputStream, response );
    }

    private class LoadConfigTask extends TimerTask
    {
        private boolean isModified( long currentTime, long lastModified )
        {
            if( ( currentTime - CHECK_CONFIG_FILES ) < lastModified )
            {
                return true;
            }
            //else
            return false;
        }

        @Override
        public void run()
        {
            boolean load = false;
            long currentTime = System.currentTimeMillis();
            if( isModified( currentTime, resourceFiles.getLastModified( ResourceFiles.AS_ALLOCATIONS ) ) )
            {
                load = true;
            }
            if( isModified( currentTime, resourceFiles.getLastModified( ResourceFiles.RIR_MAP ) ) )
            {
                load = true;
            }
            if( isModified( currentTime, resourceFiles.getLastModified( ResourceFiles.TLD_MAP ) ) )
            {
                load = true;
            }
            if( isModified( currentTime, resourceFiles.getLastModified( ResourceFiles.V4_ALLOCATIONS ) ) )
            {
                load = true;
            }
            if( isModified( currentTime,
                resourceFiles.getLastModified( ResourceFiles.V6_ALLOCATIONS ) ) )
            {
                load = true;
            }
            if( load )
            {
                try
                {
                    loadStats();
                }
                catch ( Exception e )
                {
                    getServletContext().log( "Problem loading config", e );
                }
            }
        }

        public void loadStats() throws Exception
        {
            if( getServletConfig() != null )
            {
                getServletContext().log( "Loading resource files and reloading statistics." );
            }
            resourceFiles = new ResourceFiles();
            asAllocations.loadData( resourceFiles );
            ipV4Allocations.loadData( resourceFiles );
            ipV6Allocations.loadData( resourceFiles );
            tldAllocations.loadData( resourceFiles );

            //setup statistics
            Statistics _statistics = new Statistics( resourceFiles );
            asAllocations.addAsCountersToStatistics( _statistics );
            ipV4Allocations.addIp4CountersToStatistics( _statistics );
            ipV4Allocations.addDomainRirCountersToStatistics( _statistics );
            ipV6Allocations.addIp6CountersToStatistics( _statistics );
            ipV6Allocations.addDomainRirCountersToStatistics( _statistics );
            tldAllocations.addDomainTldCountersToStatistics( _statistics );
            tldAllocations.addNsTldCountersToStatistics( _statistics );
            tldAllocations.addEntityTldCountersToStatistics( _statistics );
            statistics = _statistics;
        }
    }
}
