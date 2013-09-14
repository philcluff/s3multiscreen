package org.philcluff.multiscreen.api;

import java.net.URL;
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

        // Get the first 1000 files from S3 and shuffle them
        List<S3ObjectSummary> s3objects = s3.listObjects(BUCKET)
                .getObjectSummaries();
        Collections.shuffle(s3objects);

        for (S3ObjectSummary object : s3objects) {
            // Find the first video of the right profile in the list
            if (object.getKey().contains("flv_avc1_low")) {
                System.out.println(object.getKey());

                // Generate a one-time URL for the file, and stream it back.
                Date expiration = new java.util.Date();
                long msec = expiration.getTime();
                msec += 1000 * 60 * 60; // 1 hour.
                expiration.setTime(msec);

                GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(
                        BUCKET, object.getKey());
                generatePresignedUrlRequest.setMethod(HttpMethod.GET);
                generatePresignedUrlRequest.setExpiration(expiration);
                URL url = s3.generatePresignedUrl(generatePresignedUrlRequest);

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType("text/plain");
                resp.getWriter().write(url.toString());
                System.out.println("Generated One-Time auth URL: " + url.toString());
                return;
            }
        }

        // Otherwise, 404 :(
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.setContentType("text/plain");
        resp.getWriter().write("Could not find a valid video.");
        return;

    }
}
