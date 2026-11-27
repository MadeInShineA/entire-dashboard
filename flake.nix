{
  description = "Entire Dashboard development environment";
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };
  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
      ...
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            # Backend (Spring Boot, built with the Gradle wrapper)
            jdk25
            # Frontend (Nuxt 4)
            nodejs_24
            pnpm_10
            # Database runs in Docker (see docker-compose.yml); the client is handy for poking at it
            mariadb.client
            # Entire CLI, to produce checkpoints for the dashboard to show
            entire
          ];

          shellHook = ''
            export JAVA_HOME=${pkgs.jdk25}
            # package.json pins pnpm@10.26.0; without this pnpm downloads that exact version as a
            # generic-linux binary, which cannot run on NixOS. Use the pnpm 10 from this shell instead.
            export npm_config_manage_package_manager_versions=false
          '';
        };
      }
    );
}
