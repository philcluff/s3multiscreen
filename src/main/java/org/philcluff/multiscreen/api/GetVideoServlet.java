package org.philcluff.multiscreen.api;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;

@Controller
public class GetVideoServlet {

    private static String BUCKET;
    private AmazonS3Client s3;
    private List<String> keysReturnedRecently = new ArrayList<String>();

    @Autowired
    public void GetVideoServlet(AmazonS3Client s3) {
        this.s3 = s3;
        BUCKET = System.getenv("MULTISCREEN_BUCKET");
        System.out.println("Using bucket: " + BUCKET);
    }

    /**
     * getRandomVideo returns a URI as plain text which can be passed into a
     * media player.
     * 
     * @param req
     * @param resp
     * @throws Exception
     */
    @RequestMapping(value = "/random", method = { RequestMethod.GET })
    public void getRandomVideoUri(final HttpServletRequest req,
            final HttpServletResponse resp) throws Exception {

        S3ObjectSummary object = getVideoOfProfile("flv_avc1_high");

        // Found one! 200.
        if (object != null) {
            URL url = getPresignedUrlForObject(object);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            resp.getWriter().write(url.toString());
            System.out
                    .println("Generated One-Time auth URL: " + url.toString());
            return;
        }

        // Otherwise, 404 :(
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.setContentType("text/plain");
        resp.getWriter().write("Could not find a valid video.");
        return;

    }

    // Generate a one-time URL for the object provided
    private URL getPresignedUrlForObject(S3ObjectSummary object) {
        Date expiration = new java.util.Date();
        long msec = expiration.getTime();
        msec += 1000 * 60 * 60; // 1 hour.
        expiration.setTime(msec);

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(
                BUCKET, object.getKey());
        generatePresignedUrlRequest.setMethod(HttpMethod.GET);
        generatePresignedUrlRequest.setExpiration(expiration);
        URL url = s3.generatePresignedUrl(generatePresignedUrlRequest);
        return url;
    }

    // Get the first 1000 files from S3 and shuffle them, returning the first which matches
    // the pattern requested. Now with some limited & nasty duplicate protection.
    private S3ObjectSummary getVideoOfProfile(String profile) {
        List<S3ObjectSummary> s3objects = s3.listObjects(BUCKET)
                .getObjectSummaries();
        Collections.shuffle(s3objects);

        S3ObjectSummary fallback = null;
        for (S3ObjectSummary object : s3objects) {
            // Find the first video of the right profile in the list
            if (object.getKey().endsWith(profile + ".mp4")) {
                // Poor Man's way of checking if we've sent this one before
                if (keysReturnedRecently.contains(object.getKey())) {
                    fallback = object;
                    System.out.println("Returned this video recently, trying to find an alternative.");
                    continue;
                }
                System.out.println(object.getKey());
                keysReturnedRecently.add(object.getKey());
                return object;
            }
        }

        if (fallback != null) {
            System.out.println("Warning! returning something we returned earlier!");
            return fallback;
        }

        System.out.println("Error - Failed to find a valid video");
        return null;
    }
}
