pkg_name=wildbook
pkg_origin=lancewf
pkg_version="7.0.0-EXPERIMENTAL"
pkg_maintainer="Lance Finfrock <lancewf@gmail.com>"
pkg_license=("Apache-2.0")
pkg_deps=(core/tomcat8 core/jre8 core/postgresql)
pkg_build_deps=(core/jdk8 core/maven)

pkg_svc_user=root
pkg_svc_group=$pkg_svc_user

pkg_exports=(
  [port]=tomcat_port
)

pkg_binds=(
  [postgresql]="port superuser_name superuser_password"
)

do_prepare()
{
    export JAVA_HOME=$(hab pkg path core/jdk8)
}

do_build() {
  cp -r $PLAN_CONTEXT/../ $HAB_CACHE_SRC_PATH/$pkg_dirname
  cd ${HAB_CACHE_SRC_PATH}/${pkg_dirname}

  mvn clean install -DskipTests -Dmaven.javadoc.skip=true

  return 0
}

do_install() {
  mkdir $pkg_prefix/static
  cp -r ${HAB_CACHE_SRC_PATH}/${pkg_dirname}/target/${pkg_name}-${pkg_version}/* $pkg_prefix/static/.

  return 0
}
