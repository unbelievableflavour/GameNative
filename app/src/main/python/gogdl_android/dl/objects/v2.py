import json
import os

from gogdl_android.dl import dl_utils
from gogdl_android.dl.objects import generic
from gogdl_android import constants


class DepotFile:
    def __init__(self, item_data, product_id):
        self.flags = item_data.get("flags") or list()
        self.path = item_data["path"].replace(constants.NON_NATIVE_SEP, os.sep).lstrip(os.sep)
        if "support" in self.flags:
            self.path = os.path.join(product_id, self.path)
        self.chunks = item_data["chunks"]
        self.md5 = item_data.get("md5")
        self.sha256 = item_data.get("sha256")
        self.product_id = product_id


# That exists in some depots, indicates directory to be created, it has only path in it
# Yes that's the thing
class DepotDirectory:
    def __init__(self, item_data):
        self.path = item_data["path"].replace(constants.NON_NATIVE_SEP, os.sep).rstrip(os.sep)
    
class DepotLink:
    def __init__(self, item_data):
        self.path = item_data["path"]
        self.target = item_data["target"]


class Depot:
    def __init__(self, target_lang, depot_data):
        self.target_lang = target_lang
        self.languages = depot_data["languages"]
        self.bitness = depot_data.get("osBitness")
        self.product_id = depot_data["productId"]
        self.compressed_size = depot_data.get("compressedSize") or 0
        self.size = depot_data.get("size") or 0
        self.manifest = depot_data["manifest"]

    def check_language(self):
        status = False
        for lang in self.languages:
            status = (
                    lang == "*"
                    or self.target_lang == lang
            )
            if status:
                break
        return status

    def check_bitness(self, bitness):
        return self.bitness is None or self.bitness == bitness

    def is_language_compatible(self):
        return self.check_language()

    def is_bitness_compatible(self, bitness):
        return self.check_bitness(bitness)


class Build:
    def __init__(self, build_data, target_lang):
        self.target_lang = target_lang
        self.id = build_data["build_id"]
        self.product_id = build_data["product_id"]
        self.os = build_data["os"]
        self.branch = build_data.get("branch")
        self.version_name = build_data["version_name"]
        self.tags = build_data.get("tags") or []
        self.public = build_data.get("public", True)
        self.date_published = build_data.get("date_published")
        self.generation = build_data.get("generation", 2)
        self.meta_url = build_data["link"]
        self.password_required = build_data.get("password_required", False)
        self.legacy_build_id = build_data.get("legacy_build_id")
        self.total_size = 0
        self.install_directory = None
        self.executable = None

    def get_info(self, api_handler, bitness=64):
        manifest_json = dl_utils.get_json(api_handler, self.meta_url)
        if not manifest_json:
            return None
        
        self.install_directory = manifest_json.get("installDirectory")
        self.executable = manifest_json.get("gameExecutables", [{}])[0].get("path")
        
        depot_files = []
        for depot_data in manifest_json.get("depots", []):
            depot = Depot(self.target_lang, depot_data)
            if not depot.is_language_compatible():
                continue
            if not depot.is_bitness_compatible(bitness):
                continue
            depot_files.append(depot)
            self.total_size += depot.size
        
        return depot_files
