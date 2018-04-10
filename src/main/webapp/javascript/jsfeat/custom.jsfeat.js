        // UTILITIES
      function render_mono_image(src, dst, sw, sh, dw) {
        alert("Start render_mono_image");
                var alpha = (0xff << 24);
                for(var i = 0; i < sh; ++i) {
                    for(var j = 0; j < sw; ++j) {
                        var pix = src[i*sw+j];
                        dst[i*dw+j] = alpha | (pix << 16) | (pix << 8) | pix;
                    }
                }
            }


            function detect_keypoints(img, corners, max_allowed) {
                // detect features
				//alert("Let's get some keypoints!");
                var count = jsfeat.yape06.detect(img, corners, 17);
//alert("post yape06");
                // sort by score and reduce the count if needed
                if(count > max_allowed) {
                    jsfeat.math.qsort(corners, 0, count-1, function(a,b){return (b.score<a.score);});
                    count = max_allowed;
                }

                // calculate dominant orientation for each keypoint
                for(var i = 0; i < count; ++i) {
                    corners[i].angle = ic_angle(img, corners[i].x, corners[i].y);
                }
			
                return count;
            }

            // central difference using image moments to find dominant orientation
            var u_max = new Int32Array([15,15,15,15,14,14,14,13,13,12,11,10,9,8,6,3,0]);
            function ic_angle(img, px, py) {
                var half_k = 15; // half patch size
                var m_01 = 0, m_10 = 0;
                var src=img.data, step=img.cols;
                var u=0, v=0, center_off=(py*step + px)|0;
                var v_sum=0,d=0,val_plus=0,val_minus=0;
                
                // Treat the center line differently, v=0
                for (u = -half_k; u <= half_k; ++u)
                    m_10 += u * src[center_off+u];
                
                // Go line by line in the circular patch
                for (v = 1; v <= half_k; ++v) {
                    // Proceed over the two lines
                    v_sum = 0;
                    d = u_max[v];
                    for (u = -d; u <= d; ++u) {
                        val_plus = src[center_off+u+v*step];
                        val_minus = src[center_off+u-v*step];
                        v_sum += (val_plus - val_minus);
                        m_10 += u * (val_plus + val_minus);
                    }
                    m_01 += v * v_sum;
                }
                
                return Math.atan2(m_01, m_10);
            }

            // estimate homography transform between matched points
            function find_transform(matches, count) {
                // motion kernel
                var mm_kernel = new jsfeat.motion_model.homography2d();
                // ransac params
                var num_model_points = 4;
                var reproj_threshold = 3;
                var ransac_param = new jsfeat.ransac_params_t(num_model_points, 
                                                              reproj_threshold, 0.5, 0.99);

                var pattern_xy = [];
                var screen_xy = [];

                // construct correspondences
                for(var i = 0; i < count; ++i) {
                    var m = matches[i];
                    var s_kp = screen_corners[m.screen_idx];
                    var p_kp = pattern_corners[m.pattern_lev][m.pattern_idx];
                    pattern_xy[i] = {"x":p_kp.x, "y":p_kp.y};
                    screen_xy[i] =  {"x":s_kp.x, "y":s_kp.y};
                }

                // estimate motion
                var ok = false;
                ok = jsfeat.motion_estimator.ransac(ransac_param, mm_kernel, 
                                                    pattern_xy, screen_xy, count, homo3x3, match_mask, 1000);

                // extract good matches and re-estimate
                var good_cnt = 0;
                if(ok) {
                    for(var i=0; i < count; ++i) {
                        if(match_mask.data[i]) {
                            pattern_xy[good_cnt].x = pattern_xy[i].x;
                            pattern_xy[good_cnt].y = pattern_xy[i].y;
                            screen_xy[good_cnt].x = screen_xy[i].x;
                            screen_xy[good_cnt].y = screen_xy[i].y;
                            good_cnt++;
                        }
                    }
                    // run kernel directly with inliers only
                    mm_kernel.run(pattern_xy, screen_xy, homo3x3, good_cnt);
                } else {
                    jsfeat.matmath.identity_3x3(homo3x3, 1.0);
                }

                return good_cnt;
            }

            // non zero bits count
            function popcnt32(n) {
                n -= ((n >> 1) & 0x55555555);
                n = (n & 0x33333333) + ((n >> 2) & 0x33333333);
                return (((n + (n >> 4))& 0xF0F0F0F)* 0x1010101) >> 24;
            }

            // naive brute-force matching.
            // each on screen point is compared to all pattern points
            // to find the closest match
            function match_pattern() {
                var q_cnt = screen_descriptors.rows;
                var query_du8 = screen_descriptors.data;
                var query_u32 = screen_descriptors.buffer.i32; // cast to integer buffer
                var qd_off = 0;
                var qidx=0,lev=0,pidx=0,k=0;
                var num_matches = 0;

                for(qidx = 0; qidx < q_cnt; ++qidx) {
                    var best_dist = 256;
                    var best_dist2 = 256;
                    var best_idx = -1;
                    var best_lev = -1;

                    for(lev = 0; lev < num_train_levels; ++lev) {
                        var lev_descr = pattern_descriptors[lev];
                        var ld_cnt = lev_descr.rows;
                        var ld_i32 = lev_descr.buffer.i32; // cast to integer buffer
                        var ld_off = 0;

                        for(pidx = 0; pidx < ld_cnt; ++pidx) {

                            var curr_d = 0;
                            // our descriptor is 32 bytes so we have 8 Integers
                            for(k=0; k < 8; ++k) {
                                curr_d += popcnt32( query_u32[qd_off+k]^ld_i32[ld_off+k] );
                            }

                            if(curr_d < best_dist) {
                                best_dist2 = best_dist;
                                best_dist = curr_d;
                                best_lev = lev;
                                best_idx = pidx;
                            } else if(curr_d < best_dist2) {
                                best_dist2 = curr_d;
                            }

                            ld_off += 8; // next descriptor
                        }
                    }

                    // filter out by some threshold
                    if(best_dist < options.match_threshold) {
                        matches[num_matches].screen_idx = qidx;
                        matches[num_matches].pattern_lev = best_lev;
                        matches[num_matches].pattern_idx = best_idx;
                        num_matches++;
                    }
                    //

                    /* filter using the ratio between 2 closest matches
                    if(best_dist < 0.8*best_dist2) {
                        matches[num_matches].screen_idx = qidx;
                        matches[num_matches].pattern_lev = best_lev;
                        matches[num_matches].pattern_idx = best_idx;
                        num_matches++;
                    }
                    */

                    qd_off += 8; // next query descriptor
                }

                return num_matches;
            }

            // project/transform rectangle corners with 3x3 Matrix
            function tCorners(M, w, h) {
                var pt = [ {'x':0,'y':0}, {'x':w,'y':0}, {'x':w,'y':h}, {'x':0,'y':h} ];
                var z=0.0, i=0, px=0.0, py=0.0;

                for (; i < 4; ++i) {
                    px = M[0]*pt[i].x + M[1]*pt[i].y + M[2];
                    py = M[3]*pt[i].x + M[4]*pt[i].y + M[5];
                    z = M[6]*pt[i].x + M[7]*pt[i].y + M[8];
                    pt[i].x = px/z;
                    pt[i].y = py/z;
                }

                return pt;
            }

            function render_matches(ctx, matches, count) {
			alert("render_matches");
                
                for(var i = 0; i < count; ++i) {
                    var m = matches[i];
                    var s_kp = screen_corners[m.screen_idx];
                    var p_kp = pattern_corners[m.pattern_lev][m.pattern_idx];
                    if(match_mask.data[i]) {
                        ctx.strokeStyle = "rgb(0,255,0)";
                    } else {
                        ctx.strokeStyle = "rgb(255,0,0)";
                    }
                    ctx.beginPath();
                    ctx.moveTo(s_kp.x,s_kp.y);
                    ctx.lineTo(p_kp.x*0.5, p_kp.y*0.5); // our preview is downscaled
                    ctx.lineWidth=1;
                    ctx.stroke();
                }
            }

            function render_pattern_shape(ctx) {
				alert("render_pattern_shape");
                // get the projected pattern corners
                var shape_pts = tCorners(homo3x3.data, pattern_preview.cols*2, pattern_preview.rows*2);

                ctx.strokeStyle = "rgb(0,255,0)";
                ctx.beginPath();

                ctx.moveTo(shape_pts[0].x,shape_pts[0].y);
                ctx.lineTo(shape_pts[1].x,shape_pts[1].y);
                ctx.lineTo(shape_pts[2].x,shape_pts[2].y);
                ctx.lineTo(shape_pts[3].x,shape_pts[3].y);
                ctx.lineTo(shape_pts[0].x,shape_pts[0].y);

                ctx.lineWidth=4;
                ctx.stroke();
            }



            function render_mono_image(src, dst, sw, sh, dw) {
                var alpha = (0xff << 24);
                for(var i = 0; i < sh; ++i) {
                    for(var j = 0; j < sw; ++j) {
                        var pix = src[i*sw+j];
                        dst[i*dw+j] = alpha | (pix << 16) | (pix << 8) | pix;
                    }
                }
            }
   
      

      
         

            // our point match structure
            var match_t = (function () {
                function match_t(screen_idx, pattern_lev, pattern_idx, distance) {
                    if (typeof screen_idx === "undefined") { screen_idx=0; }
                    if (typeof pattern_lev === "undefined") { pattern_lev=0; }
                    if (typeof pattern_idx === "undefined") { pattern_idx=0; }
                    if (typeof distance === "undefined") { distance=0; }

                    this.screen_idx = screen_idx;
                    this.pattern_lev = pattern_lev;
                    this.pattern_idx = pattern_idx;
                    this.distance = distance;
                }
                return match_t;
            })();