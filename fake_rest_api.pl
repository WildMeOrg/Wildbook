#!/usr/bin/perl

{
 package Server;
 use HTTP::Server::Simple::CGI;
	use Data::Dumper;
 use base qw(HTTP::Server::Simple::CGI);
 

 sub handle_request {
     my $self = shift;
     my $cgi  = shift;

     my $path = $cgi->path_info();
warn Dumper(\%ENV);
 
warn "path=($path)";
#my $body = $cgi->param('dog');
my $body = $cgi->param('POSTDATA');
warn "body=\n------\n$body\n";
warn "---------\n\n";

warn "params:\n";
my @p = $cgi->param;
foreach my $p (@p) {
	warn "($p) -> (" . $cgi->param($p) . ")\n";
}

	print "HTTP/1.0 200 OK\n\n{\"success\": true}";


=pod

     if (ref($handler) eq "CODE") {
         print "HTTP/1.0 200 OK\r\n";
         $handler->($cgi);
         
     } else {
         print "HTTP/1.0 404 Not found\r\n";
         print $cgi->header,
               $cgi->start_html('Not found'),
               $cgi->h1('Not found'),
               $cgi->end_html;
     }

=cut

	}

 }

my $pid = Server->new(5000)->run; #->background();
warn "running on port 5000; pid=$pid\n";


sub parse_headers {
	my $foo = shift;
warn "?????????????????????????\n" . Dumper($foo);
	return;
}


