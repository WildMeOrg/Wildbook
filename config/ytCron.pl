#!/usr/bin/perl
use utf8;
use JSON;
use URI::Escape;
use Data::Dumper;

my $BASE_URL = 'http://localhost';
my $search_keyword = 'whale shark';
my $max_videos = 1;
my $tmp_prefix = '/tmp/yt';

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
	printf("- %2d %s %s\n", $ct, $v->{id}->{videoId}, $v->{snippet}->{title});
	my $res = &create($v->{id}->{videoId});
	$ct++;
	if (!$res || !$res->{success}) {
		print "     * failed to create MediaAsset; skipping\n";
		next;
	}
	if ($res->{info} =~ /already exists/) {
		print "     . MediaAsset already exists at $res->{assetId}; skipping\n";
		next;
	}
	print "     + successfully created MediaAsset $res->{assetId}\n";
	my $ext = &extract($res->{assetId});
	if (!$ext || !$ext->{success}) {
		print "     * failed to extract MediaAsset id=$res->{assetId}; skipping\n";
		next;
	}
	my $ia = &ia($ext->{frameAssets});
	if (!$ia || !$ia->{success}) {
		print "     * failed to sent to IA for detection; skipping\n";
		next;
	}
	print "     + successfully send to IA for detection as taskId=$ia->{taskId}\n";
}




sub search_for {
	my $keyword = uri_escape(shift);
	#my $cmd = "curl -s '$BASE_URL/ytSearch.jsp?keyword=$keyword'";
	my $raw_file = "$tmp_prefix-search-raw.json";
	system("curl -s '$BASE_URL/ytSearch.jsp?keyword=$keyword' > $raw_file");
	open(R, $raw_file) || die "unable to open $raw_file";
	my $raw = join('', <R>);
	close(R);
	my $json;
	eval { $json = from_json($raw); };
	die "could not parse json in $raw_file: $@" if $@;
	return $json;
}


sub create {
	my $id = shift;
	return {} unless $id;
	my $create_out = "$tmp_prefix-create-out.json";
	system("curl -s $BASE_URL/ytCreate.jsp?id=$id > $create_out");
	open(O, $create_out) || die "unable to open $create_out";
	my $raw = join('', <O>);
	close(O);
	my $json;
	eval { $json = from_json($raw); };
	die "could not parse json in $create_out: $@" if $@;
	return $json;
}


sub extract {
	my $id = shift;
	return {} unless $id;
	my $extract_out = "$tmp_prefix-extract-out.json";
	system("curl -s $BASE_URL/ytExtract.jsp?id=$id > $extract_out");
	open(O, $extract_out) || die "unable to open $extract_out";
	my $raw = join('', <O>);
	close(O);
	my $json;
	eval { $json = from_json($raw); };
	die "could not parse json in $extract_out: $@" if $@;
	return $json;
}


sub ia {
	my $ids = shift;
	return {} unless ($ids && scalar(@$ids));
	my $ia_data = to_json({ detect => { mediaAssetIds => $ids } });
	my $ia_out = "$tmp_prefix-ia-out.json";
#curl -X POST -H "Content-Type: application/json" -d '{"username":"xyz","password":"xyz"}' http://localhost:3000/api/login 
	#warn "curl -s -X POST -H 'Content-Type: application/json' -d '$ia_data' $BASE_URL/ia > $ia_out"; return;
	system("curl -s -X POST -H 'Content-Type: application/json' -d '$ia_data' $BASE_URL/ia > $ia_out");
	open(O, $ia_out) || die "unable to open $ia_out";
	my $raw = join('', <O>);
	close(O);
	my $json;
	eval { $json = from_json($raw); };
	die "could not parse json in $ia_out: $@" if $@;
	return $json;
}


