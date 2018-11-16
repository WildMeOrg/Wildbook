#!/usr/bin/perl

use utf8;
use open ':std', ':encoding(UTF-8)';
use JSON;
use URI::Escape;
use Data::Dumper;
use File::Slurp;


my $BASE_URL = 'http://localhost';
my $search_keyword = $ARGV[0] || '"whale shark"';
my $max_videos = $ARGV[1] || 1;
my $tmp_prefix = '/tmp/yt-ws.' . time;

print "searching on keyword '$search_keyword' at $BASE_URL\n";
my $search_results = &search_for($search_keyword);
#print Dumper($search_results);
die "failed search_results return value" if (!$search_results || !$search_results->{success} || !$search_results->{videos});
printf("count: %d (max %d); since %s\n", $search_results->{count}, $max_videos, $search_results->{sinceDateTime});
if ($search_results->{count} < 1) {
	print "no results on search; exiting\n";
	exit;
}

my $ct = 1;
foreach my $v (@{$search_results->{videos}}) {
	last if ($ct > $max_videos);
	printf("- %2d  %s  https://www.youtube.com/watch?v=%s  %s\n", $ct, $v->{id}->{videoId}, $v->{id}->{videoId}, $v->{snippet}->{title});
	my $res = &create($v->{id}->{videoId});
	$ct++;
	if (!$res || !$res->{success}) {
		print "     * failed to create MediaAsset (usually means not downloadable); skipping\n";
		next;
	}
	my $vurl = "$BASE_URL/obrowse.jsp?type=MediaAsset&id=" . ($res->{assetId} + 2);
	if ($res->{info} =~ /already exists/) {
		print "     . MediaAsset already exists at $res->{assetId}; (video $vurl) skipping\n";
		next;
	}
	print "     + successfully created MediaAsset $res->{assetId} (video $vurl)\n";
	my $ext = &extract($res->{assetId});
	if (!$ext || !$ext->{success} || !$ext->{frameAssets}) {
		print "     * failed to extract MediaAsset id=$res->{assetId}; skipping\n";
		next;
	}
	print "     + successfully extracted " . scalar(@{$ext->{frameAssets}}) . " frames\n";
	my $ia = &ia($ext->{frameAssets});
	if (!$ia || !$ia->{success}) {
		print "     * failed to sent to IA for detection; skipping\n";
		next;
	}
	print "     + successfully sent to IA for detection: $BASE_URL/yt.html?$ia->{taskId}\n";
}




sub search_for {
	my $keyword = uri_escape(shift);
	#my $cmd = "curl -s '$BASE_URL/ytSearch.jsp?keyword=$keyword'";
	my $raw_file = "$tmp_prefix-search-raw.json";
	my $since = (time - (2.5*60*60*24)) * 1000;
#$since = 1517097600001;
#warn "($since)";
	system("curl -s '$BASE_URL/ytSearch.jsp?keyword=$keyword&since=$since' > $raw_file");
	return &json_from_file($raw_file);

	open(R, $raw_file) || die "unable to open $raw_file";
	my $raw = join('', <R>);
	close(R);
	my $json;
	eval { $json = from_json($raw); };
	print "     !!! could not parse json in $raw_file: $@" if $@;
	return $json;
}


sub create {
	my $id = shift;
	return {} unless $id;
	my $create_out = "$tmp_prefix-create-$id-out.json";
	system("curl -s $BASE_URL/ytCreate.jsp?id=$id > $create_out");
	return &json_from_file($create_out);

	open(O, $create_out) || die "unable to open $create_out";
	my $raw = join('', <O>);
	close(O);
	my $json;
	eval { $json = from_json($raw); };
	print "     !!! could not parse json in $create_out: $@" if $@;
	return $json;
}


sub extract {
	my $id = shift;
	return {} unless $id;
	my $extract_out = "$tmp_prefix-extract-$id-out.json";
	system("curl -s $BASE_URL/ytExtract.jsp?id=$id > $extract_out");
	return &json_from_file($extract_out);

	open(O, $extract_out) || die "unable to open $extract_out";
	my $raw = join('', <O>);
	close(O);
	my $json;
	eval { $json = from_json($raw); };
	print "     !!! could not parse json in $extract_out: $@" if $@;
	return $json;
}


sub ia {
	my $ids = shift;
	return {} unless ($ids && scalar(@$ids));
	my $ia_data = to_json({ detect => { mediaAssetIds => $ids } });
	my $ia_out = "$tmp_prefix-ia-" . $ids->[0] . "-out.json";
	system("curl -s -X POST -H 'Content-Type: application/json' -d '$ia_data' $BASE_URL/ia > $ia_out");
	return &json_from_file($ia_out);

	open(O, $ia_out) || die "unable to open $ia_out";
	my $raw = join('', <O>);
	close(O);
	my $json;
	eval { $json = from_json($raw); };
	print "     !!! could not parse json in $ia_out: $@" if $@;
	return $json;
}



sub json_from_file {
	my $filename = shift;
	sleep 1;  #cuz i dont trust that system() was done?
	return unless -e $filename;
	my $content = read_file($filename);
	return unless $content;
	my $json;
	eval { $json = from_json($content); };
	if ($@) {
#print "---------($filename)---------\n" . substr($content, 0, 1000) . "\n-------------------\n";
		#print "     !!! could not parse json in $filename: $@";
		print "     !!! could not parse json in $filename\n";
	}
	return $json;
}
