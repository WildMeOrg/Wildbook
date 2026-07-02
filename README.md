# Wildbook

Wildbook is an open source software framework to support mark-recapture, molecular ecology, and social ecology studies. The biological and statistical communities already support a number of excellent tools, such as Program MARK,GenAlEx, and SOCPROG for use in analyzing wildlife data. Wildbook is a complementary software application that:

- provides a scalable and collaborative platform for intelligent wildlife data storage and management, including advanced, consolidated searching
- provides an easy-to-use software suite of functionality that can be extended to meet the needs of wildlife projects, especially where individual identification is used
- provides an API to support the easy export of data to cross-disciplinary analysis applications (e.g., GenePop ) and other software (e.g., Google Earth)
- provides a platform that supports the exposure of data in biodiversity databases (e.g., GBIF and OBIS)
- provides a platform for animal biometrics that supports easy data access and facilitates matching application deployment for multiple species

## Getting Started with Wildbook

Wildbook is a long-standing tool that support a wide variety of researchers and species. 
The Wild Me team is working on revamping the tool as a true open source project, so if you have ideas and are excited to help, reach out to us on the [Wild Me Development Discord](https://discord.gg/zw4tr3RE4R)!

### Quick Start with Docker

The easiest way to run Wildbook locally is with Docker Compose. This builds the full application (including the frontend) and starts all required services.

**Prerequisites:** Docker and Docker Compose installed on your system.

```bash
# 1. Create your .env file from the repo root's .env defaults
#    (defaults already provided no changes needed for local dev)

cp devops/development/_env.template .env

# 2. Start all services

docker compose up -d
```

The first build will take a few minutes as it compiles the Java project and builds the React frontend. Subsequent starts are faster.

**What's running:**

| Service      | Description                      | Port                   |
| ------------ | -------------------------------- | ---------------------- |
| `wildbook`   | Tomcat with Wildbook application | `8080`                 |
| `db`         | PostgreSQL database              | `5433`                 |
| `opensearch` | Search index                     | `9200`                 |
| `mailhog`    | Email capture for dev            | SMTP `1025`, UI `8025` |
| `wbia`       | Image analysis (Wildbook IA)     | `82`                   |

Once started, open **http://localhost:8080** in your browser. Default login: `tomcat` / `tomcat123`.

Outbound emails are captured by MailHog. View them at **http://localhost:8025**.

**Note:** OpenSearch requires a higher `vm.max_map_count` on Linux. Run once:

```bash
sudo sysctl -w vm.max_map_count=262144
```
### Legacy development
For legacy docker build and instructions on locally building the .war file see [`devops/README.md`](devops/README.md) for detailed instructions.

### Frontend-Only Development

If you are only making changes to the React frontend, see [`frontend/README.md`](frontend/README.md) for a faster rebuild cycle.

## Machine Learning in Wildbook

Wildbook leverages [Wildbook IA (WBIA)](https://github.com/WildbookOrg/wildbook-ia) as the machine learning engine, which pulls data from Wildbook servers to detect features in images and identify individual animals. WBIA brings massive-scale computer vision to wildlife research.

## Need direct help?

Wild Me (wildme.org) engineering staff provide support for Wildbook. You can contact us at: opensource@wildme.org

We provide support during regular office hours on Mondays and Tuesdays.

## Support resources

- User documentation is available at [Wild Me Documentation](http://wildbook.docs.wildme.org)
- For user support, visit the [Wild Me Community Forum](https://community.wildme.org)
- For contribution guidelines, visit [Wildbook Code Contribution Guidelines](https://wildbook.docs.wildme.org/contribute/code-guide.html)
- For developer support, visit the [Wild Me Development Discord](https://discord.gg/zw4tr3RE4R)
- Email the team at opensource@wildme.org

## History

Wildbook started as a collaborative software platform for globally-coordinated whale shark (Rhincodon typus ) research as deployed in the Wildbook for Whale Sharks (now part of http://www.sharkbook.ai). After many requests to use our software outside of whale shark research, it is now an open source, community-maintained standard for mark-recapture studies.

Wildbook is a trademark of [Conservation X Labs](https://conservationxlabs.com/), a 501(c)(3) non-profit organization, and is supported by the [Wild Me](https://wildme.org) team.
