package RabbitMQRelay;

use v5.24;
use strict;

use nginx;
use JSON;


####  apt-get install libssl-dev libdbd-pg-perl
#### cpan Net::AMQP::RabbitMQ
use DBI;
use DBD::Pg;
use DBD::Pg qw(:pg_types);
use Net::AMQP::RabbitMQ;

#### libuuid-tiny-perl
use UUID::Tiny ':std';

use Data::Dumper;

use File::Slurp;
#use File::Path 'make_path';

my $db_json;
#my $db_json = from_json(scalar(read_file('/opt/flux_order_transaction/transactor_db.json', { err_mode => 'quiet' })));
#die "could not find/read db credentials via order_db.json!!" unless ($db_json && $db_json->{db});

my $db;
#die "initial DB connect failed!!!" unless &check_db;

my $mq;
my $rabbit_config;
&rabbit_config;

sub check_db {
    return 1;  #disabled for now
    return 1 if ($db && $db->ping);
    warn "### db/ping failed, attempting (re)connect ########################\n";
    $db = DBI->connect("DBI:Pg:dbname=" . $db_json->{db} . ";host=" . $db_json->{host}, $db_json->{user}, $db_json->{pass}, {'RaiseError' => 1});
    return 1 if $db;
    warn "!!! db (re)connect failed! cause: " . DBI->errstr . " !!!!!!!!!!!!!!!!\n";
    return 0;
}

sub handler {
    my $req = shift;
    return 501 unless &check_db;
    return DECLINED if ($req->request_method ne "POST");
    return OK if ($req->has_request_body(\&handle_post));
    return HTTP_BAD_REQUEST;
}



sub handle_post {
    my $req = shift;
    my $qname = substr($req->uri, 9);
    my @d = localtime;
    my $rtn = {
        meta => {
            ip => $req->remote_addr,
            xf => $req->header_in('X-Forwarded-For'),
            'time' => time,
        },
        success => JSON::true,
    };

    my $id = create_uuid_as_string(UUID_V4);
    $rtn->{transactionId} = $id;
    warn sprintf("%s [%04d-%02d-%02d %02d:%02d:%02d] %s/%s %s [%s %d]\n", $id, $d[5] + 1900, $d[4] + 1, $d[3], $d[2], $d[1], $d[0],
        $req->remote_addr, $req->header_in('X-Forwarded-For'), $req->uri, $req->header_in('Content-Type'), $req->header_in('Content-Length'));

    #if ($req->uri =~ /^\/api\/Order\//) {
        #note: for /api/Order/(target) there is no authkey -- authentication comes from cookie (so we use authkey 'session')
        ##my $sess = &Auth::get_session_from_cookie_header($req->header_in('cookie'));
        ####return 401 unless $sess; #"any" user can put into transaction table, permissions is dealt with by munger based on session (user)
        #####return 588 unless ($target =~ /^(store|place|preview|forward|delete)$/);  #our list of valid targets (from app)
        ### for store/place/preview we *must* have these headers
        #$order_id = $req->header_in('x-order-id') || return 590;
    #return 593 unless $target;
    #return 401 unless ($auth_key =~ /^(devAuthKey|mwO01|mwA01|session)$/);

    my $body = $req->request_body;
warn "$id =============(body_length=" . length($body) . ")======================\n";
    return 599 unless ($body && (length($body) > 8));
    my $body_json;
    eval { $body_json = from_json($body); };
    return 598 unless $body_json;
#warn "body_json(((" . Dumper($body_json) . ")))";
    if ($body_json->{_queue}) {
        $qname = $body_json->{_queue};
        delete($body_json->{_queue});
    }
    $rtn->{meta}->{queue} = $qname;

    return 597 unless &queue_job($qname, { transactionId => $id, content => $body_json });

    $req->send_http_header("application/javascript");  ##this sends 200 return code!
    #$rtn->{warning} = "unable to add job to queue; unknown target?" unless $queued;
    ##$rtn->{external_id} = $external_id if $external_id;
    $req->print(to_json($rtn));
    return OK;
}



sub dump_file {
    my $base_dir = '/var/spool/flux_incoming_order_transactions';
    my ($id, $target, $body) = @_;

    my @d = localtime;
    my $dir = sprintf('%s/%04d%02d%02d/%s/%s/%s', $base_dir, $d[5] + 1900, $d[4] + 1, $d[3], $target, substr($id, 0, 1), substr($id, 1, 1));
    make_path($dir);
    my $filename = "$dir/$id.raw";
    write_file($filename, {binmode => ':raw', err_mode => 'carp'}, $body);
    return $filename;
}



sub queue_job {
    my ($queue, $msg) = @_;
    if (!grep(/^$queue$/, @{$rabbit_config->{queues}})) {
        warn "*** no rabbit queue found for (queue=$queue, id=$msg->{transactionId}); continuing without queueing\n";
        return;
    }

    warn "+++ queueing id=$msg->{transactionId} to $queue\n";
    $msg->{'time'} = time;
    $msg->{queue} = $queue;
    #delivery_mode 2 is "persistent" (for ack support from consumer side)
    my $count = 4;
    my $conn_ok = $mq->is_connected;
    while (($count > 0) && !$conn_ok) {
        warn localtime . " [ct $count] \$mq is NOT connected; retrying\n";
        sleep 1;
        &rabbit_connect;
        $count--;
        $conn_ok = $mq->is_connected;
    }
    return unless $conn_ok;

    my $r = $mq->publish(1, $queue, to_json($msg), {}, { delivery_mode => 2 });
#TODO how to confirm publish!!!!?????
    return $msg->{transactionId};
}

sub rabbit_config {
    warn ">>> starting rabbit_config\n";
    my $rj = from_json(scalar(read_file('/tmp/rabbitmq.json', { err_mode => 'quiet' })));
#warn "????" . Dumper($rj);
    die "could not read rabbitmq.json!!!" unless $rj;
    $mq = Net::AMQP::RabbitMQ->new();
    $rabbit_config = $rj;
    &rabbit_connect;
    warn "> valid queues: " . @{$rabbit_config->{queues}} . "\n";
}

sub rabbit_connect {
#warn "?????>>>>" . Dumper($rabbit_config);
#return;
    $mq->connect($rabbit_config->{host}, {
        vhost => $rabbit_config->{vhost},
        user => $rabbit_config->{user},
        password => $rabbit_config->{pass},
    });
    $mq->channel_open(1);
}



1;
