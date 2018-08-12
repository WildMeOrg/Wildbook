# -*- coding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
import functools
import numpy as np
import scipy.cluster.hierarchy
from scipy.spatial import distance


def haversine(latlon1, latlon2):
    r"""
    Calculate the great circle distance between two points
    on the earth (specified in decimal degrees)

    Args:
        latlon1 (tuple): (lat, lon)
        latlon2 (tuple): (lat, lon)

    Returns:
        float : distance in kilometers

    References:
        en.wikipedia.org/wiki/Haversine_formula
        gis.stackexchange.com/questions/81551/matching-gps-tracks
        stackoverflow.com/questions/4913349/haversine-distance-gps-points

    Example:
        >>> from occurrence_blackbox import *  # NOQA
        >>> import scipy.spatial.distance as spdist
        >>> import functools
        >>> latlon1 = [-80.21895315, -158.81099213]
        >>> latlon2 = [  9.77816711,  -17.27471498]
        >>> kilometers = haversine(latlon1, latlon2)
        >>> result = ('kilometers = %s' % (kilometers,))
        >>> print(result)
        kilometers = 11930.9093642
    """
    # convert decimal degrees to radians
    lat1, lon1 = np.radians(latlon1)
    lat2, lon2 = np.radians(latlon2)

    # haversine formula
    dlon = lon2 - lon1
    dlat = lat2 - lat1
    a = (np.sin(dlat / 2) ** 2) + np.cos(lat1) * np.cos(lat2) * (np.sin(dlon / 2) ** 2)
    c = 2 * np.arcsin(np.sqrt(a))

    EARTH_RADIUS_KM = 6367
    kilometers = EARTH_RADIUS_KM * c
    return kilometers


def timespace_distance(pt1, pt2, km_per_sec=.02):
    """
    Computes distance between two points in space and time.
    Time is converted into spatial units using km_per_sec

    Args:
        pt1 (tuple) : (seconds, lat, lon)
        pt2 (tuple) : (seconds, lat, lon)
        km_per_sec (float): reasonable animal walking speed

    Returns:
        float: distance in kilometers

    Example:
        >>> from occurrence_blackbox import *  # NOQA
        >>> import scipy.spatial.distance as spdist
        >>> import functools
        >>> km_per_sec = .02
        >>> latlon1 = [-80.21895315, -158.81099213]
        >>> latlon2 = [  9.77816711,  -17.27471498]
        >>> pt1 = [360.0] + latlon1
        >>> pt2 = [0.0] + latlon2
        >>> kilometers = timespace_distance(pt1, pt2)
        >>> result = ('kilometers = %s' % (kilometers,))
        >>> print(result)
        kilometers = 2058.6323187
    """
    (sec1, lat1, lon1) = pt1
    (sec2, lat2, lon2) = pt2
    # Get pure gps distance
    km_dist = haversine((lat1, lon1), (lat2, lon2))
    # Get distance in seconds and convert to km
    sec_dist = np.abs(sec1 - sec2) * km_per_sec
    # Add distances
    timespace_dist = km_dist + sec_dist
    return timespace_dist


def cluster_timespace(X_data, thresh, km_per_sec=.02):
    """
    Agglometerative clustering of time/space data

    Args:
        X_data (ndarray) : Nx3 array where columns are (seconds, lat, lon)
        thresh (float) : threshold in kilometers

    References:
        http://docs.scipy.org/doc/scipy-0.14.0/reference/generated/
            scipy.cluster.hierarchy.linkage.html
            scipy.cluster.hierarchy.fcluster.html

    Notes:
        # Visualize spots
        http://www.darrinward.com/lat-long/?id=2009879

    Example:
        >>> # DISABLE_DOCTEST
        >>> from occurrence_blackbox import *  # NOQA
        >>> # Nx1 matrix denoting groundtruth locations (for testing)
        >>> X_name = np.array([0, 1, 1, 1, 1, 1, 2, 2, 2])
        >>> # Nx3 matrix where each columns are (time, lat, lon)
        >>> X_data = np.array([
        >>>     (0, 42.727985, -73.683994),  # MRC
        >>>     (0, 42.657414, -73.774448),  # Park1
        >>>     (0, 42.658333, -73.770993),  # Park2
        >>>     (0, 42.654384, -73.768919),  # Park3
        >>>     (0, 42.655039, -73.769048),  # Park4
        >>>     (0, 42.657872, -73.764148),  # Park5
        >>>     (0, 42.876974, -73.819311),  # CP1
        >>>     (0, 42.862946, -73.804977),  # CP2
        >>>     (0, 42.849809, -73.758486),  # CP3
        >>> ])
        >>> thresh = 5.0  # kilometers
        >>> X_labels = cluster_timespace(X_data, thresh)
        >>> result = ('X_labels = %r' % (X_labels,))
        >>> print(result)
        X_labels = array([3, 2, 2, 2, 2, 2, 1, 1, 1], dtype=int32)
    """
    # Compute pairwise distances between all inputs
    dist_func = functools.partial(timespace_distance, km_per_sec=km_per_sec)
    condenced_dist_mat = distance.pdist(X_data, dist_func)
    # Compute heirarchical linkages
    linkage_mat = scipy.cluster.hierarchy.linkage(condenced_dist_mat,
                                                  method='single')
    # Cluster linkages
    X_labels = scipy.cluster.hierarchy.fcluster(linkage_mat, thresh,
                                                criterion='distance')
    return X_labels


if __name__ == '__main__':
    r"""
    CommandLine:
        python occurrence_blackbox.py --lat 42.727985 42.657414 42.658333 42.654384 --lon -73.683994 -73.774448 -73.770993 -73.768919 --sec 0 0 0 0
        # Should return
        X_labels = [2, 1, 1, 1]

    """
    import argparse
    parser = argparse.ArgumentParser(description='Compute agglomerative cluster')
    parser.add_argument('--lat', type=float, nargs='*', help='list of latitude coords')
    parser.add_argument('--lon', type=float, nargs='*', help='list of longitude coords')
    parser.add_argument('--sec', type=float, nargs='*', help='list of POSIX_TIMEs in seconds')
    parser.add_argument('--thresh', type=float, nargs=1, default=1., help='threshold in kilometers')
    parser.add_argument('--km_per_sec', type=float, nargs=1, default=.02, help='reasonable animal speed in km/s')
    args = parser.parse_args()
    sec = [0] * len(args.lat) if args.sec is None else args.sec
    X_data = np.vstack([sec, args.lat, args.lon]).T
    X_labels = cluster_timespace(X_data, args.thresh, km_per_sec=args.km_per_sec)
    print('X_labels = %r' % (X_labels.tolist(),))
