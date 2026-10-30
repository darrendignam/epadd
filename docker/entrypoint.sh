#!/bin/sh
set -eu

readonly VALID_MODES="appraisal processing discovery delivery"

case " ${VALID_MODES} " in
    *" ${EPADD_MODE} "*) ;;
    *)
        echo "epadd-entrypoint: invalid EPADD_MODE '${EPADD_MODE}' (expected one of: ${VALID_MODES})" >&2
        exit 64
        ;;
esac

for dir in "${EPADD_BASE_DIR}" "${EPADD_SETTINGS_DIR}" "$(dirname "${EPADD_LOG_FILE}")" "$(dirname "${EPADD_PROPERTIES_FILE}")"; do
    if ! mkdir -p "${dir}" 2>/dev/null || [ ! -w "${dir}" ]; then
        echo "epadd-entrypoint: ${dir} is not writable by uid $(id -u); fix the volume ownership" >&2
        exit 73
    fi
done

if [ ! -f "${EPADD_PROPERTIES_FILE}" ] || [ "${EPADD_REGENERATE_PROPERTIES:-true}" = "true" ]; then
    {
        echo "epadd.mode=${EPADD_MODE}"
        echo "epadd.language=${EPADD_LANGUAGE}"
        echo "epadd.base.dir=${EPADD_BASE_DIR}"
        echo "epadd.settings.dir=${EPADD_SETTINGS_DIR}"
        if [ -n "${EPADD_ADMIN_CONTACT}" ]; then
            echo "admin=${EPADD_ADMIN_CONTACT}"
        fi
    } > "${EPADD_PROPERTIES_FILE}"
fi

export CATALINA_OPTS="${EPADD_JAVA_OPTS} \
 -Djava.awt.headless=true \
 -Dfile.encoding=UTF-8 \
 -Duser.home=/data/home \
 -Depadd.properties=${EPADD_PROPERTIES_FILE} \
 -Dmuse.log=${EPADD_LOG_FILE} \
 ${CATALINA_OPTS:-}"

exec "$@"
