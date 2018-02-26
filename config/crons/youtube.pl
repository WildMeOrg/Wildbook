#!/usr/bin/perl
use JSON;
use Data::Dumper;
use LWP::UserAgent;

my $hostPrefix = 'http://pod.scribble.com:6767/uptest';
my $dir = '/home/jon/wildbook/yt';
my $keyword = 'whale shark';
my $max = 1;

my $ua = LWP::UserAgent->new;
$ua->agent("youtube-cron");


my $since = &getSince;
print "since=$since; keyword=$keyword; dir=$dir; max=$max\n";

my $url = "$hostPrefix/ytSearch.jsp?keyword=$keyword" . ($since ? "&since=$since" : "");
my $find = &jget($url);

if (!$find) {
	print "$url failed -> $find\n";
	exit 1;
}

if (!$find->{success}) {
	print "$url failed: $find->{error}\n -> " . Dumper($find) . "\n";
	exit 2;
}

if (!$find->{count}) {
	print "no videos found for '$keyword'\n";
	exit;
}

my $count = 0;
foreach my $vid (@{$find->{videos}}) {
	$count++;
	last if ($max && ($count > $max));
	#print Dumper($vid);
	printf("%3d [%s] %s http://youtube.com/watch?v=%s\n", $count, $vid->{id}->{videoId}, $vid->{snippet}->{title}, $vid->{id}->{videoId});
	my $curl = "$hostPrefix/ytCreate.jsp?id=" . $vid->{id}->{videoId};
	my $created = &jget($curl);
	if (!$created || !$created->{success} || !$created->{assetId}) {
		print "    failed or no assetId: $created->{error}\n";
		next;
	}
	print "    success: assetId = $created->{assetId} $hostPrefix/obrowse.jsp?type=MediaAsset&id=$created->{assetId}\n";
#print Dumper($created);

	my $eurl = "$hostPrefix/ytExtract.jsp?createEncounter=true&id=" . $created->{assetId};
	my $extracted = &jget($eurl);
          #'assetId' => '131979',
          #'encounterId' => 'eda3e8be-79f0-4773-8d2a-ae70db50b99d',
          #'success' => bless( do{\(my $o = 1)}, 'JSON::PP::Boolean' ),
          #'frameAssets' => [
#print Dumper($extracted);

	if (!$extracted || !$extracted->{success} || !$extracted->{frameAssets}) {
		print "    failed: $extracted->{error}\n";
		next;
	}
	printf("    %d frames extracted, created %s\n", scalar(@{$extracted->{frameAssets}}),
		"$hostPrefix/obrowse.jsp?type=Encounter&id=" . $extracted->{encounterId});

	my $body = { detect => { mediaAssetIds => [ @{$extracted->{frameAssets}}[0..3] ] } };
	my $det = &jpost("$hostPrefix/ia", $body);
#print Dumper($det);
	if (!$det || !$det->{success}) {
		print "    failed: $det->{error}\n";
		next;
	}
	printf("    taskId=%s detection success\n", $det->{taskId});
}


sub jget {
	my $url = shift;
	my $raw = &hget($url);
	return unless $raw;
	my $parsed;
	eval {
		$parsed = from_json($raw);
	};
	return $parsed;
}

sub hget {
	my $url = shift;
	my $req = HTTP::Request->new(GET => $url);
	my $res = $ua->request($req);
	return $res->content if $res->is_success;
	warn $res->status_line . "\n";
	return;
}


sub jpost {
	my ($url,$body) = @_;
	my $raw = &hpost($url, $body);
	return unless $raw;
	my $parsed;
	eval {
		$parsed = from_json($raw);
	};
	return $parsed;
}

sub hpost {
	my ($url,$body) = @_;
	my $req = HTTP::Request->new(POST => $url);
	$req->content_type('application/javascript');
	$req->content(to_json($body));
	my $res = $ua->request($req);
	return $res->content if $res->is_success;
	warn $res->status_line . "\n";
	return;
}

sub getSince {
	return;
}
