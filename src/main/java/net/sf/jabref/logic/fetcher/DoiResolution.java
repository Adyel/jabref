/*  Copyright (C) 2015 JabRef contributors.
    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package net.sf.jabref.logic.fetcher;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import net.sf.jabref.logic.io.MimeTypeDetector;
import net.sf.jabref.logic.util.DOI;
import net.sf.jabref.model.entry.BibEntry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * FullTextFinder implementation that follows the DOI resolution redirects and scans for a full-text PDF URL.
 */
public class DoiResolution implements FullTextFinder {
    private static final Log LOGGER = LogFactory.getLog(DoiResolution.class);

    @Override
    public Optional<URL> findFullText(BibEntry entry) throws IOException {
        Objects.requireNonNull(entry);
        Optional<URL> pdfLink = Optional.empty();

        Optional<DOI> doi = DOI.build(entry.getField("doi"));

        if(doi.isPresent()) {
            String sciLink = doi.get().getURLAsASCIIString();

            // follow all redirects and scan for a single pdf link
            try {
                if (!sciLink.isEmpty()) {
                    Connection connection = Jsoup.connect(sciLink).followRedirects(true).ignoreHttpErrors(true);
                    Document html = connection.get();
                    // scan for PDF
                    Elements elements = html.body().select("[href]");
                    List<Optional<URL>> links = new ArrayList<Optional<URL>>();

                    for (Element element : elements) {
                        String href = element.attr("abs:href");
                        // Only check if pdf is included in the link
                        // FIXME: see https://github.com/lehner/LocalCopy for scrape ideas
                        if(href.contains("pdf")) {
                            if(MimeTypeDetector.isPdfContentType(href)) {
                                links.add(Optional.of(new URL(href)));
                            }
                        }
                    }
                    // return if only one link was found (high accuracy)
                    if (links.size() == 1) {
                        LOGGER.info("Fulltext PDF found @ " + sciLink);
                        pdfLink = links.get(0);
                    }
                }
            } catch(IOException e) {
                LOGGER.warn("DoiResolution fetcher failed: ", e);
            }
        }
        return pdfLink;
    }
}
