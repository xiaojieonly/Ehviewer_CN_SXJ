#!/usr/bin/env python3
"""
使用Deepseek API翻译strings.xml文件
"""

import os
import re
import json
import time
import requests
import xml.etree.ElementTree as ET
from pathlib import Path
import argparse
from typing import Dict, List, Tuple


class DeepseekTranslator:
    def __init__(self, api_key, model='deepseek-chat'):
        self.api_key = api_key
        self.model = model
        self.translated_cache = {}
        
    def translate_text(self, text, source_lang, target_lang):
        """翻译文本"""
        if not text or text.strip() == '':
            return text
            
        # 检查缓存
        cache_key = f"{text}_{source_lang}_{target_lang}"
        if cache_key in self.translated_cache:
            return self.translated_cache[cache_key]
        
        try:
            # 构建提示词
            prompt = f"请将以下文本从{source_lang}翻译成{target_lang}：\n\n{text}"
            
            # 调用Deepseek API
            url = "https://api.deepseek.com/v1/chat/completions"
            headers = {
                'Authorization': f'Bearer {self.api_key}',
                'Content-Type': 'application/json'
            }
            data = {
                'model': self.model,
                'messages': [
                    {'role': 'system', 'content': '你是一个专业的翻译助手，擅长将文本翻译成各种语言。'},
                    {'role': 'user', 'content': prompt}
                ],
                'temperature': 0.3,
                'max_tokens': 1000
            }
            
            response = requests.post(url, headers=headers, json=data, timeout=30)
            response.raise_for_status()
            
            result = response.json()
            if 'choices' in result and len(result['choices']) > 0:
                translated = result['choices'][0]['message']['content'].strip()
                # 缓存结果
                self.translated_cache[cache_key] = translated
                return translated
            
            return text
            
        except Exception as e:
            print(f"翻译失败: {e}")
            return text


class StringsTranslator:
    def __init__(self, base_file: str, translator: DeepseekTranslator):
        self.base_file = Path(base_file)
        self.translator = translator
        self.base_strings = {}
        
    def parse_base_file(self):
        """解析基础strings.xml文件"""
        tree = ET.parse(self.base_file)
        root = tree.getroot()
        
        for elem in root:
            if elem.tag == 'string':
                name = elem.get('name')
                if name:
                    self.base_strings[name] = elem.text or ''
            elif elem.tag == 'plurals':
                name = elem.get('name')
                if name:
                    self.base_strings[f'plurals_{name}'] = elem
    
    def get_language_name(self, dir_name):
        """根据目录名获取语言名称"""
        language_names = {
            'values-de': '德语',
            'values-en': '英语',
            'values-es': '西班牙语',
            'values-fr': '法语',
            'values-ja': '日语',
            'values-ko': '韩语',
            'values-th': '泰语',
            'values-zh-rCN': '简体中文',
            'values-zh-rHK': '繁体中文（香港）',
            'values-zh-rTW': '繁体中文（台湾）',
        }
        
        return language_names.get(dir_name, '目标语言')
    
    def translate_file(self, file_path: Path, dry_run=False):
        """翻译文件"""
        if not file_path.exists():
            print(f"文件不存在: {file_path}")
            return
        
        # 获取语言名称
        dir_name = file_path.parent.name
        target_lang = self.get_language_name(dir_name)
        
        print(f"翻译 {dir_name} ({target_lang})")
        
        # 解析文件
        tree = ET.parse(file_path)
        root = tree.getroot()
        
        translated_count = 0
        for elem in root:
            if elem.tag == 'string':
                name = elem.get('name')
                if name and name in self.base_strings:
                    base_text = self.base_strings[name]
                    current_text = elem.text or ''
                    
                    # 检查是否需要翻译
                    if current_text != base_text:
                        # 翻译文本
                        if not dry_run:
                            translated_text = self.translator.translate_text(
                                base_text, '英语', target_lang
                            )
                            elem.text = translated_text
                        else:
                            translated_text = f"[翻译] {base_text}"
                        
                        translated_count += 1
                        print(f"  {name}: {current_text} -> {translated_text}")
            
            elif elem.tag == 'plurals':
                name = elem.get('name')
                if name and f'plurals_{name}' in self.base_strings:
                    base_elem = self.base_strings[f'plurals_{name}']
                    if isinstance(base_elem, ET.Element):
                        for item in elem:
                            quantity = item.get('quantity')
                            # 找到基础文件中对应的item
                            for base_item in base_elem:
                                if base_item.get('quantity') == quantity:
                                    base_text = base_item.text or ''
                                    current_text = item.text or ''
                                    
                                    if current_text != base_text:
                                        if not dry_run:
                                            translated_text = self.translator.translate_text(
                                                base_text, '英语', target_lang
                                            )
                                            item.text = translated_text
                                        else:
                                            translated_text = f"[翻译] {base_text}"
                                        
                                        translated_count += 1
                                        print(f"  {name} ({quantity}): {current_text} -> {translated_text}")
        
        if not dry_run:
            # 格式化并保存
            self._indent_xml(root)
            tree.write(file_path, encoding='utf-8', xml_declaration=True)
            print(f"  已保存，翻译了 {translated_count} 个字符串")
        else:
            print(f"  模拟运行，将翻译 {translated_count} 个字符串")
    
    def _indent_xml(self, elem, level=0):
        """为XML元素添加缩进"""
        i = "\n" + level * "    "
        if len(elem):
            if not elem.text or not elem.text.strip():
                elem.text = i + "    "
            if not elem.tail or not elem.tail.strip():
                elem.tail = i
            for child in elem:
                self._indent_xml(child, level + 1)
            if not child.tail or not child.tail.strip():
                child.tail = i
        else:
            if level and (not elem.tail or not elem.tail.strip()):
                elem.tail = i
    
    def process_all_languages(self, dry_run=False):
        """处理所有语言文件"""
        res_dir = self.base_file.parent.parent
        
        # 获取所有目标语言目录（不包含英文源文件目录）
        language_dirs = []
        for item in res_dir.iterdir():
            if item.is_dir() and item.name.startswith('values-') and item.name != 'values-en':
                # 排除配置特定的覆盖文件
                config_patterns = ['values-land', 'values-large', 'values-night', 'values-sw', 'values-v']
                is_config_override = any(item.name.startswith(pattern) for pattern in config_patterns)
                if not is_config_override:
                    language_dirs.append(item)
        
        print(f"找到 {len(language_dirs)} 个语言目录")
        
        for lang_dir in language_dirs:
            lang_file = lang_dir / 'strings.xml'
            if lang_file.exists():
                self.translate_file(lang_file, dry_run)


def find_base_strings_file():
    """自动查找基础strings.xml文件，优先使用英文源文件"""
    # 尝试在当前目录和父目录中查找英文源文件
    search_paths = [
        Path.cwd() / 'app' / 'src' / 'main' / 'res' / 'values-en' / 'strings.xml',
        Path.cwd().parent / 'app' / 'src' / 'main' / 'res' / 'values-en' / 'strings.xml',
        Path.cwd() / 'app' / 'src' / 'main' / 'res' / 'values' / 'strings.xml',
    ]
    
    for path in search_paths:
        if path.exists():
            return path
    
    # 如果没有找到，尝试在res目录中查找
    res_dirs = []
    for path in [Path.cwd(), Path.cwd().parent]:
        if (path / 'app' / 'src' / 'main' / 'res').exists():
            res_dirs.append(path / 'app' / 'src' / 'main' / 'res')
    
    for res_dir in res_dirs:
        values_dir = res_dir / 'values'
        if values_dir.exists():
            strings_file = values_dir / 'strings.xml'
            if strings_file.exists():
                return strings_file
    
    return None


def main():
    parser = argparse.ArgumentParser(description='使用Deepseek API翻译strings.xml文件')
    parser.add_argument('--base', help='基础strings.xml文件路径（可选，会自动查找）')
    parser.add_argument('--api-key', required=True, help='Deepseek API密钥')
    parser.add_argument('--model', default='deepseek-chat', 
                       help='Deepseek模型名称')
    parser.add_argument('--dry-run', action='store_true', 
                       help='只显示将要进行的翻译，不实际修改文件')
    
    args = parser.parse_args()
    
    # 查找基础文件
    if args.base:
        base_file = Path(args.base)
        if not base_file.exists():
            print(f"错误: 指定的基础文件不存在: {args.base}")
            return 1
    else:
        base_file = find_base_strings_file()
        if not base_file:
            print("错误: 无法自动找到基础strings.xml文件")
            print("请使用 --base 参数指定文件路径")
            return 1
        print(f"自动找到基础文件: {base_file}")
    
    if not args.api_key:
        print("错误: 需要提供Deepseek API密钥")
        return 1
    
    # 创建翻译器
    translator = DeepseekTranslator(args.api_key, args.model)
    
    # 创建字符串翻译器
    strings_translator = StringsTranslator(str(base_file), translator)
    strings_translator.parse_base_file()
    
    print(f"基础文件: {base_file}")
    print(f"找到 {len(strings_translator.base_strings)} 个基础字符串")
    print(f"使用模型: {args.model}")
    
    # 处理所有语言文件
    strings_translator.process_all_languages(dry_run=args.dry_run)
    
    if args.dry_run:
        print("\n注意: 这是模拟运行，没有实际修改文件")
    
    return 0


if __name__ == '__main__':
    exit(main())